#!/bin/bash

set -e

CALL_DIR="$(pwd)"
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
RELEASES_DIR="/home/tonikelope/CORONAPOKER_RELEASES_TEMPLATES"

figlet -t CoronaPoker BUILDER

if [ ! -d "${DIR}/target" ]; then
	cd "${DIR}"
	git pull
	mvn clean install
fi

RELEASE_VERSION=$(ls ${DIR}/target/CoronaPoker-*-jar-with-dependencies.jar | grep -o -E '[0-9]+.[0-9]+')

echo -e "RELEASE VERSION: ${RELEASE_VERSION}\n"

if [ -f "${DIR}/.last_build" ]; then

	LAST_BUILD_VERSION=$(cat "${DIR}/.last_build")

	if [[ "${RELEASE_VERSION}" = "${LAST_BUILD_VERSION}" || "$(curl -s -I -o /dev/null -w '%{http_code}' https://github.com/tonikelope/coronapoker/releases/tag/v${RELEASE_VERSION})" == "200" ]]; then
		cd $DIR
		mvn clean
		echo -e "\nAVISO: RELEASE YA CREADA y SUBIDA ANTERIORMENTE. BYEZ!"
		exit
	fi
fi

cd $RELEASES_DIR

if [ ! -f CoronaPoker_"${RELEASE_VERSION}".jar ]; then
	cp -f "${DIR}/target/CoronaPoker-${RELEASE_VERSION}-jar-with-dependencies.jar" "CoronaPoker_${RELEASE_VERSION}.jar"
fi

if [ ! -f "CoronaPokerLINUX_${RELEASE_VERSION}_portable.zip" ]; then
	cp -f "CoronaPoker_${RELEASE_VERSION}.jar" CoronaPokerLINUX/jar/CoronaPoker.jar
	zip -r "CoronaPokerLINUX_${RELEASE_VERSION}_portable.zip" CoronaPokerLINUX/
fi

if [ ! -f "CoronaPokerMACOSx64_${RELEASE_VERSION}_portable.zip" ]; then
        cp -f "CoronaPoker_${RELEASE_VERSION}.jar" CoronaPokerMACOSx64/CoronaPoker.app/Contents/MacOS/jar/CoronaPoker.jar
        zip -r "CoronaPokerMACOSx64_${RELEASE_VERSION}_portable.zip" CoronaPokerMACOSx64/
fi

if [ ! -f "CoronaPokerMACOSaarch64_${RELEASE_VERSION}_portable.zip" ]; then
        cp -f "CoronaPoker_${RELEASE_VERSION}.jar" CoronaPokerMACOSaarch64/CoronaPoker.app/Contents/MacOS/jar/CoronaPoker.jar
        zip -r "CoronaPokerMACOSaarch64_${RELEASE_VERSION}_portable.zip" CoronaPokerMACOSaarch64/
fi

if [ ! -f "CoronaPokerWINDOWS32_${RELEASE_VERSION}_portable.zip" ]; then
        cp -f "CoronaPoker_${RELEASE_VERSION}.jar" CoronaPokerWINDOWS32/jar/CoronaPoker.jar
        zip -r "CoronaPokerWINDOWS32_${RELEASE_VERSION}_portable.zip" CoronaPokerWINDOWS32/
fi

if [ ! -f "CoronaPokerWINDOWS_${RELEASE_VERSION}_portable.zip" ]; then
        cp -f "CoronaPoker_${RELEASE_VERSION}.jar" CoronaPokerWINDOWS/jar/CoronaPoker.jar
        zip -r "CoronaPokerWINDOWS_${RELEASE_VERSION}_portable.zip" CoronaPokerWINDOWS/
fi


echo -e "\nCREANDO RELEASE v${RELEASE_VERSION} en github..."

cd $RELEASES_DIR

/home/tonikelope/github-release.sh tonikelope/coronapoker "v${RELEASE_VERSION}" -- CoronaPoker*"${RELEASE_VERSION}"*.zip CoronaPoker*"${RELEASE_VERSION}"*.jar chilean_mod.zip




#echo -e "\n\n\nCREANDO PAQUETE DE ArchLinux AUR v${RELEASE_VERSION}...\n"

#MD5SUM=$(md5sum /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerLINUX_"${RELEASE_VERSION}"_portable.zip | grep -o -E '^[0-9a-f]+')

#cd /home/tonikelope/TRASTERO/AUR/coronapoker-bin/

#cat PKGBUILD_TEMPLATE | sed -e "s/__VERSION__/${RELEASE_VERSION}/g" | sed -e "s/__MD5SUM__/${MD5SUM}/g" > PKGBUILD

#makepkg --printsrcinfo > .SRCINFO

#git add .

#git commit -m "${RELEASE_VERSION}"

#git push




echo -e "\n\nLIMPIANDO...\n"

cd $RELEASES_DIR

rm CoronaPoker*.zip CoronaPoker*.jar

cd $DIR

mvn clean

echo -ne "${RELEASE_VERSION}" > .last_build

echo -e "\nTODO BIEN. BYEZ!"

cd $CALL_DIR
