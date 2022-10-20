#!/bin/bash

set -e

figlet -t CoronaPoker BUILDER

if [ ! -d /home/tonikelope/TRASTERO/CoronaPoker/target ]; then
	cd /home/tonikelope/TRASTERO/CoronaPoker/
	mvn clean install
fi

RELEASE_VERSION=$(ls /home/tonikelope/TRASTERO/CoronaPoker/target/CoronaPoker-*-jar-with-dependencies.jar | grep -o -E '[0-9]+.[0-9]+')

echo -e "RELEASE VERSION: ${RELEASE_VERSION}\n"

cd /home/tonikelope/TRASTERO/CoronaPokerReleases/

if [ ! -f CoronaPokerLINUX_"${RELEASE_VERSION}"_portable.zip ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPoker/target/CoronaPoker-"${RELEASE_VERSION}"-jar-with-dependencies.jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerLINUX/jar/CoronaPoker.jar
	zip -r CoronaPokerLINUX_"${RELEASE_VERSION}"_portable.zip CoronaPokerLINUX/
fi

if [ ! -f CoronaPokerMACOS_"${RELEASE_VERSION}"_portable.zip ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPoker/target/CoronaPoker-"${RELEASE_VERSION}"-jar-with-dependencies.jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerMACOS/CoronaPoker.app/Contents/MacOS/jar/CoronaPoker.jar
	zip -r CoronaPokerMACOS_"${RELEASE_VERSION}"_portable.zip CoronaPokerMACOS/
fi

if [ ! -f CoronaPokerWINDOWS32_"${RELEASE_VERSION}"_portable.zip ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPoker/target/CoronaPoker-"${RELEASE_VERSION}"-jar-with-dependencies.jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerWINDOWS32/jar/CoronaPoker.jar
	zip -r CoronaPokerWINDOWS32_"${RELEASE_VERSION}"_portable.zip CoronaPokerWINDOWS32/
fi

if [ ! -f CoronaPokerWINDOWS_"${RELEASE_VERSION}"_portable.zip ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPoker/target/CoronaPoker-"${RELEASE_VERSION}"-jar-with-dependencies.jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPokerWINDOWS/jar/CoronaPoker.jar
	zip -r CoronaPokerWINDOWS_"${RELEASE_VERSION}"_portable.zip CoronaPokerWINDOWS/
fi

if [ ! -f CoronaPoker_"${RELEASE_VERSION}".jar ]; then
	cp -f /home/tonikelope/TRASTERO/CoronaPoker/target/CoronaPoker-"${RELEASE_VERSION}"-jar-with-dependencies.jar /home/tonikelope/TRASTERO/CoronaPokerReleases/CoronaPoker_"${RELEASE_VERSION}".jar
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

echo -e "\nTODO BIEN. BYEZ!"