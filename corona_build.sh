#!/bin/bash

set -e

figlet -t CoronaPoker BUILDER

if [ ! -d /home/tonikelope/TRASTERO/CoronaPoker/target ]; then
	cd /home/tonikelope/TRASTERO/CoronaPoker/
	mvn clean install
fi

RELEASE_VERSION=$(ls /home/tonikelope/TRASTERO/CoronaPoker/target/CoronaPoker-*-jar-with-dependencies.jar | grep -o -E '[0-9]+.[0-9]+')

echo -e "RELEASE VERSION: ${RELEASE_VERSION}\n"

if [ -f /home/tonikelope/TRASTERO/CoronaPoker/.last_build ]; then

	LAST_BUILD_VERSION=$(cat /home/tonikelope/TRASTERO/CoronaPoker/.last_build)

	if [[ "${RELEASE_VERSION}" = "${LAST_BUILD_VERSION}" || "$(curl -s -I -o /dev/null -w '%{http_code}' https://github.com/tonikelope/coronapoker/releases/tag/v${RELEASE_VERSION})" == "200" ]]; then
		cd /home/tonikelope/TRASTERO/CoronaPoker/
		mvn clean
		echo -e "\nAVISO: RELEASE YA CREADA y SUBIDA ANTERIORMENTE. BYEZ!"
		exit
	fi
fi

cd /home/tonikelope/TRASTERO/CoronaPokerReleases/

if [ ! -f CoronaPoker_"${RELEASE_VERSION}".jar ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPoker/target/CoronaPoker-"${RELEASE_VERSION}"-jar-with-dependencies.jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker_"${RELEASE_VERSION}".jar
fi

if [ ! -f CoronaPokerLINUX_"${RELEASE_VERSION}"_portable.zip ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker_"${RELEASE_VERSION}".jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerLINUX/jar/CoronaPoker.jar
	zip -r CoronaPokerLINUX_"${RELEASE_VERSION}"_portable.zip CoronaPokerLINUX/
fi

if [ ! -f CoronaPokerMACOSx64_"${RELEASE_VERSION}"_portable.zip ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker_"${RELEASE_VERSION}".jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerMACOSx64/CoronaPoker.app/Contents/MacOS/jar/CoronaPoker.jar
	zip -r CoronaPokerMACOSx64_"${RELEASE_VERSION}"_portable.zip CoronaPokerMACOSx64/
fi

if [ ! -f CoronaPokerMACOSaarch64_"${RELEASE_VERSION}"_portable.zip ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker_"${RELEASE_VERSION}".jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerMACOSaarch64/CoronaPoker.app/Contents/MacOS/jar/CoronaPoker.jar
	zip -r CoronaPokerMACOSaarch64_"${RELEASE_VERSION}"_portable.zip CoronaPokerMACOSaarch64/
fi

if [ ! -f CoronaPokerWINDOWS32_"${RELEASE_VERSION}"_portable.zip ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker_"${RELEASE_VERSION}".jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerWINDOWS32/jar/CoronaPoker.jar
	zip -r CoronaPokerWINDOWS32_"${RELEASE_VERSION}"_portable.zip CoronaPokerWINDOWS32/
fi

if [ ! -f CoronaPokerWINDOWS_"${RELEASE_VERSION}"_portable.zip ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker_"${RELEASE_VERSION}".jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerWINDOWS/jar/CoronaPoker.jar
	zip -r CoronaPokerWINDOWS_"${RELEASE_VERSION}"_portable.zip CoronaPokerWINDOWS/
fi

echo -e "\nCREANDO RELEASE v${RELEASE_VERSION} en github..."

/opt/github-release.sh tonikelope/coronapoker "v${RELEASE_VERSION}" -- /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker*"${RELEASE_VERSION}"*.zip /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker*"${RELEASE_VERSION}"*.jar /home/tonikelope/TRASTERO/CoronaPokerReleases/chilean_mod.zip

echo -e "\n\n\nCREANDO PAQUETE DE ArchLinux AUR v${RELEASE_VERSION}...\n"

MD5SUM=$(md5sum /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerLINUX_"${RELEASE_VERSION}"_portable.zip | grep -o -E '^[0-9a-f]+')

cd /home/tonikelope/TRASTERO/AUR/coronapoker-bin/

cat PKGBUILD_TEMPLATE | sed -e "s/__VERSION__/${RELEASE_VERSION}/g" | sed -e "s/__MD5SUM__/${MD5SUM}/g" > PKGBUILD

makepkg --printsrcinfo > .SRCINFO

git add .

git commit -m "${RELEASE_VERSION}"

git push

echo -e "\n\nLIMPIANDO...\n"

cd /home/tonikelope/TRASTERO/CoronaPoker/

mvn clean

rm /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker*.zip /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker*.jar

echo -ne "${RELEASE_VERSION}" > /home/tonikelope/TRASTERO/CoronaPoker/.last_build

echo -e "\nTODO BIEN. BYEZ!"