/* Run Unit Tests in Docker Image.
 *
 * Run the unit test suite in a Docker image.
 *
 * These test report artifacts will be published to Jenkins.
 * The /home/bin/run_tests.sh scripts should generate them.
 *
 * Test Results:    unittest.xml
 * Code Coverage:   coverage.xml
 * Static Analysis: flake8.log
 *
 * DSL: runUnitTestsDockerImage("short_image_name")
 *
 * Required Plugins:
 *	"Docker Plugins", "Git Plugin"
 * 	"JUnit Plugin", "Cobertura Plugin", "Warnings Plugin"
 */

def getServerUser(String tier, String yamlFileDirectory = '.') {
	// Returns the server username or null when the YAML has none.
	try {
		def composeYaml = readYaml(file: "${yamlFileDirectory}/docker-compose-${tier}.yml")
		return composeYaml.services.web.user
	} catch(Exception e) {
		echo("YAML File \"docker-compose-${tier}.yml\" is unreadable...use 2-space indentation")
		return null
	}
}

def call(String imageName,
	 int healthyCoverageAbove = 85,
	 int unstableCoverageBelow = 85,
	 int failureCoverageBelow = 65) {
	def fullImageName = buildDockerImage.fullImageName(imageName)
	def unitTestImage = docker.image(fullImageName)

	// Skip Testing when 0% health is OK.
	boolean skipTests = (healthyCoverageAbove <= 0)

	if (skipTests || unitTestImage == null) {
		echo "Skip Unit Tests: unstable at ${healthyCoverageAbove}% or no image=${fullImageName}"
		currentBuild.result = 'UNSTABLE'
	}
	else {
		// Start docker container and execute run_tests.sh
		script {
			// every tier should have the same username.
			String username = getServerUser('prod')
			if (null == username) {
				error('docker-compose YAML file must declare "user:"')
			}

			sh """ mkdir ${env.WORKSPACE}/test-reports \\
&& chmod 777 ${env.WORKSPACE}/test-reports \\
&& docker run --rm \\
	--user="${username}" \\
	--entrypoint="/home/bin/run_tests.sh" \\
	--volume="${env.WORKSPACE}/test-reports:/tmp/test-reports" ${fullImageName}
"""

			// Publish unit test, coverage, and static analysis reports.
			junit healthScaleFactor: 10.0, testResults: '**/unittest.xml'

			// JUnit thresholds do not go to FAILURE, which we want.
			// See Bug: https://issues.jenkins-ci.org/browse/JENKINS-2734
			// Work Around: https://support.cloudbees.com/hc/en-us/articles/218866667-How-to-abort-a-Pipeline-build-if-JUnit-tests-fail-
			Boolean badTestResults = false
			if (currentBuild.result == 'UNSTABLE') {
				badTestResults = true
			}

			// Coverage Targets: "healthy, bad, unstable"
			// healthy:  Report health as 100% when coverage is higher than X%.
			// bad:      Report health as 0% when coverage is less than Y%.
			// unstable: Mark the build as unstable when coverage is less than Z%.
			String coverageTargets = [
				healthyCoverageAbove,
				failureCoverageBelow, // must swap positions
				unstableCoverageBelow
			].join(', ')
			cobertura(
				coberturaReportFile: '**/coverage.xml',
				classCoverageTargets:       coverageTargets,
				conditionalCoverageTargets: coverageTargets,
				fileCoverageTargets:        coverageTargets,
				lineCoverageTargets:        coverageTargets,
				methodCoverageTargets:      coverageTargets,
				packageCoverageTargets:     coverageTargets,
				autoUpdateHealth: true,
				autoUpdateStability: true,
				failUnhealthy: true,
				failUnstable: false,
				maxNumberOfBuilds: 0,
				onlyStable: false,
				sourceEncoding: 'ASCII',
				zoomCoverageChart: false)

			warnings(
				parserConfigurations: [[
					parserName: 'Pep8',
					pattern: '**/flake8.log'
				]],
				canRunOnFailed: true,
				unstableTotalAll: '0', // unstable when even one warning.
				failedTotalAll: '15',  // build fails with 15 warnings.
				usePreviousBuildAsReference: true)

			// Report an error when there are bad test results.
			if (badTestResults) {
				error("Unit Tests have bad results ... fail the build")
			}
		}
	}
}
