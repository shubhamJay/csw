#!groovy
try{
    ansiColor('xterm') {
        node('master') {
            def failBuild = false

            stage('Checkout') {
                git 'https://github.com/tmtsoftware/csw-prod.git'
            }

            stage('Build') {
                sh "sbt scalafmt --test"
                sh "sbt -Dcheck.cycles=true clean scalastyle compile"
            }

            stage('Unit and Component Tests') { // Component tests cover the scenario of multiple components in single container
                try {
                    sh "sbt -DenableCoverage=true test:test"
                }
                catch (Exception e) {
                    currentBuild.result = 'FAILED'
                    failBuild = true
                }
                try {
                    sh "sbt coverageReport"
                }
                catch (Exception ex) {
                    failBuild = true
                }
                sh "sbt coverageAggregate"
                if (failBuild == true)
                    sh "exit 1"
            }

            stage('Multi-Jvm Test') { // These tests cover the scenario of multiple components in multiple containers on same machine.
                sh "sbt csw-location/multi-jvm:test"
                sh "sbt csw-config/multi-jvm:test"
            }

            stage('Multi-Node Test') { // These tests cover the scenario of multiple components in multiple containers on different machines.
                sh "sbt -DenableCoverage=false csw-location/multi-node-test"
                sh "sbt -DenableCoverage=false csw-config/multi-node-test"
            }

            stage('Package') {
                sh "./universal_package.sh"
                stash name: "repo"
            }
        }

        node('JenkinsNode1') {
            stage('Multi-Container Docker') {
                unstash "repo"
                sh "./integration/scripts/runner.sh"
            }

            stage('Multi-NICs Docker') {
                sh "./integration/scripts/multiple_nic_test.sh"
            }
        }

        node('master') {
            stage('Deploy') {
                sh "./publish.sh"
            }
        }
    }
}
catch (Exception e) {
    currentBuild.result = "FAILED"
    throw e
}
finally {
    node('master') {
        stage("Report") {
            try{
                publishJunitReport()
                publishScoverageReport()
            }
            finally {
                sendNotification(currentBuild.result)
            }
        }
    }
}

def sendNotification(String buildStatus = 'STARTED') {
    buildStatus = buildStatus ?: 'SUCCESSFUL'

    def colorName = 'RED'
    def colorCode = '#FF0000'
    def subject = "${buildStatus}: Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'"
    def summary = "${subject} (${env.BUILD_URL})"
    def details = '${JELLY_SCRIPT,template="html"}'

    // Override default values based on build status
    if (buildStatus == 'STARTED') {
        color = 'YELLOW'
        colorCode = '#FFFF00'
    } else if (buildStatus == 'SUCCESSFUL') {
        color = 'GREEN'
        colorCode = '#00FF00'
    } else {
        color = 'RED'
        colorCode = '#FF0000'
        emailext(
                    subject: subject,
                    body: details,
                    to: "tmt-csw@thoughtworks.com"
            )
    }

    slackSend(color: colorCode, message: summary)

}

def publishJunitReport() {
    junit '**/target/test-reports/*.xml'
}

def publishScoverageReport() {
    publishHTML(target: [
            allowMissing         : true,
            alwaysLinkToLastBuild: false,
            keepAll              : true,
            reportDir            : './target/scala-2.12/scoverage-report',
            reportFiles          : 'index.html',
            reportName           : "Scoverage Report"
    ])
}
