<h1>CoronaPoker</h1>

[![Maintenance](https://img.shields.io/badge/Maintained%3F-yes-green.svg)](https://GitHub.com/Naereen/StrapDown.js/graphs/commit-activity) [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0) [![Linux](https://svgshare.com/i/Zhy.svg)](https://svgshare.com/i/Zhy.svg) [![macOS](https://svgshare.com/i/ZjP.svg)](https://svgshare.com/i/ZjP.svg) [![Windows](https://svgshare.com/i/ZhY.svg)](https://svgshare.com/i/ZhY.svg)

<p align="justify">This is the project of a perfectionist, who one day during the confinement of COVID19, came up with the idea of developing the most complete and fun open source game of Texas hold'em for his friends. I hope you enjoy playing it as much as I enjoy programming it. Carpe diem.</p>

<p align="center"><a href="https://github.com/tonikelope/coronapoker/releases/latest" target="_blank"><img src="https://raw.githubusercontent.com/tonikelope/megabasterd/master/src/main/resources/images/linux-mac-windows.png"></a></p>

<h3 align="center"><a href="https://www.youtube.com/watch?v=lAUBjjssKGk"><b>GAMEPLAY VIDEO</b></a></h3>

<p align="center"><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/1.png"><br><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/13.png"></a></p>

## Some features:
- Cross platform.
- No central servers nor third parties logging things (just you and your friends).
- Point-to-point encryption (DH + AES 128).
- Password protected games.
- Secure by design with a (modest and homemade) anticheat module.
- Up to 10 simultaneous human/bot players.
- Intuitive interface (with comfortable key shortcuts).
- Global in-game zoom (UHD resolution supported).
- TRUE RANDOM shuffle (powered by https://www.random.org).
- Cool sounds, decks, mats, cinematics and 3D card effects.
- ALL-IN side pots fully supported.
- "Dead button" rule for BB/SB/DE positions.
- IWTSTH rule available by default (can be dis/enabled by the server during the game).
- All blinds stuff adjustable by the server before or during the game.
- Rebuy available.
- Waiting room chat with emojis, custom gifs and urls support.
- Google Meet link QR generator for easy sharing with all players.
- Text to speech fast chat and sending of custom gifs during the game.
- 3 view modes for different screen sizes (normal, compact, and super compact) and low brightness mode.
- Very high tolerance to network/power failures (games can be resumed from exact stop point).
- It is possible to pause the game at any time and add new players.
- Game log and statistics.
- English and spanish language.
- Customizable: create and share your MODs with custom font, decks, sounds and cinematics.

## GET CORONAPOKER

### [OPTION A (Recommended)] DOWNLOAD <a href="https://github.com/tonikelope/coronapoker/releases/latest">LATEST RELEASE</a>

<i>Important: if you plan to distribute CoronaPoker as a package for your favorite Linux distribution and you wish to keep anticheat module enabled you MUST use this option (otherwise the binaries will be different for each player, generating false positives).</i>

https://aur.archlinux.org/packages/coronapoker-bin</p>

### [OPTION B] BUILD CORONAPOKER FROM SOURCE:

<i>Use this option if for any reason you want to compile your own version of CoronaPoker and distribute it to your friends (if you are one of the friends, my security advice is that you all use option A).</i>

#### Step 1 (install coronahmac.jar as Maven local package):
```
mvn install:install-file -Dfile=coronaHMAC_x.x.x.jar -DgroupId=com.tonikelope.coronahmac -DartifactId=coronahmac -Dversion=x.x.x -Dpackaging=jar
```
#### Step 2 (build):
```
mvn clean install
```
##### Extra: What is CoronaHMAC?
<p align="justify">It is a small module that is loaded at game startup to mitigate the server-player trying to cheat using a different version of the game than other players. For obvious reasons the source code is not available (if for any reason you are not comfortable using closed source dependencies, go to OPTION C). 

### [OPTION C] BUILD CORONAPOKER FROM SOURCE WITHOUT CORONAHMAC:

##### Step 1 (edit pom.xml):
###### 1.1 Change MAIN CLASS:
```
<mainClass>com.tonikelope.coronahmac.M</mainClass>
```
to:

```
<mainClass>com.tonikelope.coronapoker.Init</mainClass>
```

###### 1.2 Delete coronahmac dependency:
```
<dependency>
   <groupId>com.tonikelope.coronahmac</groupId>
   <artifactId>coronahmac</artifactId>
   <version>1.1.24</version>
</dependency>
```

##### Step 2 (build):
```
mvn clean install
```



