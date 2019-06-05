/** Docker Registry Configuration.
 *
 * This file stores the private Docker registry configuration constants.
 **/

package com.opt;

class DockerRegistryConfig {
	// Private Docker Registry 
	static final String DOCKER_REGISTRY_URL = '<ADD HERE>'    //'https://dockercentral.it.att.com:5100'
	static final String DOCKER_NAMESPACE = '<ADD HERE>'   // 'dockercentral.it.att.com:5100/com.att.dev.argos'

	// NOTE: DOCKER_CRED_ID matches Jenkins Global credentials-ID.
	// See Docker Workflow Pipeline Plugin for help.
	static final String DOCKER_CRED_ID = 'docker-credentials-id'

	// SSH Publish Plugin Target Directory.
	// docker-compose Directory on remote deployment
	static final String DOCKER_COMPOSE_DIR = '<ADD HERE>'   // '/home/ad718x/test-docker-deploy'
}
