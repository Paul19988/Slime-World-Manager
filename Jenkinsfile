pipeline {
    agent any
    options {
        gitLabConnection('Gitlab')
    }
    triggers {
        gitlab(triggerOnPush: true, triggerOnMergeRequest: true, branchFilterType: 'All')
    }
     post {
          failure {
            updateGitlabCommitStatus name: 'build', state: 'failed'
          }
          success {
            updateGitlabCommitStatus name: 'build', state: 'success'
          }
        }
    tools {
        maven '3.6.3'
        jdk '8u221'
    }
    stages {
        stage ('Initialize') {
            steps {
                sh '''
                    wget https://cdn.getbukkit.org/spigot/spigot-1.16.1.jar && mvn install:install-file -Dfile=spigot-1.16.1.jar -DgroupId=org.spigotmc -DartifactId=spigot -Dversion=1.16.1-R0.1-SNAPSHOT -Dpackaging=jar
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
                '''
            }
        }

        stage ('Build') {
            steps {
                dir('.'){
                    sh 'mvn -Dmaven.test.failure.ignore=true install'
                }
            }
        }

        stage('CleanWorkspace') {
            steps {
                cleanWs()
            }
        }
    }
}

