#!/usr/bin/env bash

currentDir=$(pwd)
keycloakDir=${currentDir}
keycloakVersion=4.6.0
keycloakBinaryUnzipped=keycloak-${keycloakVersion}.Final
keycloakBinaryZipped=${keycloakBinaryUnzipped}.tar.gz
script_name=$0

port=8081
host="0.0.0.0"
userName=""
password=""
testMode=false

importJsonPath="../conf/auth_service/tmt-realm-export.json"

# Run from the directory containing the script
cd "$( dirname "${BASH_SOURCE[0]}" )"

function unzipTar {
    echo "Unzipping  $keycloakDir/$keycloakBinaryZipped"
    tar -xzf ${keycloakBinaryUnzipped}.tar.gz
    echo "Unzipped $keycloakDir/$keycloakBinaryZipped"
}

function checkIfKeycloakIsInstalled {
    if test -x ${keycloakDir}/${keycloakBinaryUnzipped}; then
    echo "$keycloakBinaryUnzipped is already installed"
    elif test -e ${keycloakDir}/${keycloakBinaryUnzipped}.tar.gz ; then
    echo "$keycloakDir/$keycloakBinaryZipped is already downloaded."
    cd ${keycloakDir}
    unzipTar
    else
      echo "Installing $keycloakBinaryUnzipped"
      test -d ${keycloakDir} || mkdir -p ${keycloakDir}
      curl https://downloads.jboss.org/keycloak/${keycloakVersion}.Final/${keycloakBinaryUnzipped}.tar.gz --output ${keycloakDir}/${keycloakBinaryZipped}
      cd ${keycloakDir}
      unzipTar
    fi
}

function parse_cmd_args {
    while [[ $# -gt 0 ]]
        do
            key="$1"

            case ${key} in
                --port | -p)
                   port=$2
                   ;;
                --host | -h)
                    host=$2
                    ;;
                --dir | -d)
                   keycloakDir=$2
                   ;;
                --user | -u)
                    userName=$2
                    ;;
                --password)
                    password=$2
                    ;;
                --testMode)
                    testMode=true
                    ;;
                --help)
                    usage
                    ;;
            esac
        shift
    done

    if [[ ${userName} == "" ]]; then
         echo "[ERROR] Username is missing. Please provide username (--user | -u)"
         exit 1
    fi

    if [[ ${password} == "" ]]; then
         echo "[ERROR] password is missing. Please provide password (--password)"
         exit 1
    fi

}

function usage {
    echo
    echo -e "usage: $script_name [--port | -p <port>] [--host | -h <host>] [--dir | -d <dir>] [--user | -u <user>] [--password  <password>]\n"

    echo "Options:"
    echo "  --port | -p <port>              start AAS on provided port, default: 8081"
    echo "  --host | -h <host>              start AAS on provided ip address, default: starts on ip associated with provided interface and localhost if to be accessed by same machine"
    echo "  --dir | -d <dir>                installs AAS binary on provided directory, default: current working dir"
    echo "  --user | -u <user_name>         add AAS with provided user as admin"
    echo "  --password <password>           add provided password for admin user"
    exit 1
}


function addAdminUser {
    cd ${keycloakDir}/${keycloakBinaryUnzipped}/bin
    echo "[INFO] Adding user"
    sh add-user-keycloak.sh --user ${userName} -p ${password}
}

function is_AAS_running {
    local http_code=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:${port}/auth/${userName}/realms)
    if [[ $http_code -eq 401 ]]; then
        return 0
    else
        return 1
    fi
}

function wait_till_AAS_starts {
    until is_AAS_running; do
        echo AAS still not running, waiting 5 seconds
        sleep 5
    done

    echo AAS is running, proceeding with configuration
}


function addTestUsers {
    cd ${keycloakDir}/${keycloakBinaryUnzipped}/bin
    echo "[INFO] Adding test users"
    sh add-user-keycloak.sh -u kevin -p abcd -r TMT
    sh add-user-keycloak.sh -u frank -p abcd -r TMT
}

function associateRoleToTestUsers {
    wait_till_AAS_starts
    cd ${keycloakDir}/${keycloakBinaryUnzipped}/bin
    echo "[INFO] Associate roles to test users"
    sh kcadm.sh config credentials --server http://${host}:${port}/auth --realm master --user ${userName} --password ${password}
    sh kcadm.sh add-roles --uusername kevin --rolename admin --cclientid csw-config-server -r TMT
}

function startAndRegister {
    cd ${currentDir}
    pwd
    echo "[INFO] starting server at $host:$port"
    sh csw-location-agent --name AAS --http "auth" -c "sh ${keycloakDir}/${keycloakBinaryUnzipped}/bin/standalone.sh -Djboss.bind.address=${host} -Djboss.http.port=${port} -Dkeycloak.migration.action=import -Dkeycloak.migration.provider=singleFile -Dkeycloak.migration.file=${currentDir}/${importJsonPath}" -p "$port"
}

function start {
    parse_cmd_args "$@"
    checkIfKeycloakIsInstalled
    addAdminUser
    if ${testMode} ; then addTestUsers ; fi
    if ${testMode} ; then
     associateRoleToTestUsers &
    fi
    startAndRegister
}

start "$@"