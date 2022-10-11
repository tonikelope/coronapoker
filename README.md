<h1>CoronaPoker</h1>

[![Maintenance](https://img.shields.io/badge/Maintained%3F-yes-green.svg)](https://GitHub.com/Naereen/StrapDown.js/graphs/commit-activity) [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0) [![Linux](https://svgshare.com/i/Zhy.svg)](https://svgshare.com/i/Zhy.svg) [![macOS](https://svgshare.com/i/ZjP.svg)](https://svgshare.com/i/ZjP.svg) [![Windows](https://svgshare.com/i/ZhY.svg)](https://svgshare.com/i/ZhY.svg)

This is the project of a perfectionist, who one day during the confinement of COVID19, came up with the idea of developing the most complete and fun open source game of Texas hold'em for his friends. I hope you enjoy playing it as much as I enjoy programming it. Carpe diem.

<h3 align="center"><a href="https://www.youtube.com/watch?v=lAUBjjssKGk"><b>GAMEPLAY VIDEO</b></a></h3>
<p align="center"><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/1.png"><br><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/13.png"></a></p>

## Some features:
- No central servers or third party logging things (just you and your friends).
- Point-to-point encryption (DH + AES 128).
- Up to 10 simultaneous human/bot players.
- Very intuitive interface.
- Global zoom: ultra high resolution supported.
- TRUE RANDOM shuffle (powered by https://www.random.org).
- Cool sounds, decks, mats and 3D card effects.
- All-in side pots fully supported.
- "Dead button" strategy for BB/SB/DE positions.
- IWTSTH rule enabled by default (can be de/activated by the server during the game).
- All blinds stuff adjustable by the server during the game.
- Fun cinematics for tense moments.
- Text to speech in-game chat and custom gifs.
- 3 view modes for different screen sizes (normal, compact, and super compact).
- Very high tolerance to network/power failures (games can be resumed from exact stop point).
- Customizable: create and share your MODs with custom decks and cinematics.
- Cross platform.
- (Modest) anticheat module.

CoronaPoker was built using Java and (the old, but not obsolete) <a href="https://en.wikipedia.org/wiki/Swing_(Java)">Swing API</a>

<i>Advice: if you plan to distribute CoronaPoker as a package for your favorite Linux distribution, it is HIGHLY RECOMMENDED that you use OPTION A or OPTION C.</i>

https://aur.archlinux.org/packages/coronapoker-bin

### [OPTION A (Recommended)] DOWNLOAD <a href="https://github.com/tonikelope/coronapoker/releases/latest">LATEST RELEASE</a>

### [OPTION B] BUILD CORONAPOKER FROM SOURCE:

#### Step 1 (install coronahmac.jar as Maven local package):
```
mvn install:install-file -Dfile=coronaHMAC_x.x.x.jar -DgroupId=com.tonikelope.coronahmac -DartifactId=coronahmac -Dversion=x.x.x -Dpackaging=jar
```
#### Step 2 (build):
```
mvn clean install
```
##### Extra: What is CoronaHMAC?
It is a small module that is loaded at game startup to mitigate any player (including the server) trying to cheat using a hacked version of the game. For obvious reasons the source code is not available (if for any reason you are not comfortable using closed source dependencies, go to OPTION C). 
Note: it is possible to ignore the jar integrity check warnings by adding the following to <i>/home/user/.coronapoker/coronapoker.properties</i> file -> <i>binary_check=false</i>

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



