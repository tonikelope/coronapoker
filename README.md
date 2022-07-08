<h1>CoronaPoker</h1>

[![Maintenance](https://img.shields.io/badge/Maintained%3F-yes-green.svg)](https://GitHub.com/Naereen/StrapDown.js/graphs/commit-activity) [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0) [![Linux](https://svgshare.com/i/Zhy.svg)](https://svgshare.com/i/Zhy.svg) [![macOS](https://svgshare.com/i/ZjP.svg)](https://svgshare.com/i/ZjP.svg) [![Windows](https://svgshare.com/i/ZhY.svg)](https://svgshare.com/i/ZhY.svg)

This is the project of a perfectionist, who one day during the confinement of COVID19, came up with the idea of developing the most complete and fun Open Source game of Texas hold'em for his friends. I hope you enjoy playing it as much as I enjoy programming it. Carpe diem.

<h3 align="center"><a href="https://www.youtube.com/watch?v=lAUBjjssKGk"><b>GAMEPLAY VIDEO</b></a></h3>
<p align="center"><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/1.png"><br><img src="https://raw.githubusercontent.com/tonikelope/coronapoker/gh-pages/screenshots/13.png"></a></p>

CoronaPoker was built using Java and (the old, but not obsolete) <a href="https://en.wikipedia.org/wiki/Swing_(Java)">Swing API</a>

### [OPTION A (Recommended)] DOWNLOAD <a href="https://github.com/tonikelope/coronapoker/releases/latest">LATEST RELEASE</a>

### [OPTION B] BUILD CORONAPOKER FROM SOURCE:

#### Step 1 (install coronahmac.jar as Maven local package):
```
mvn install:install-file -Dfile=coronaHMAC_x.x.x.jar -DgroupId=com.tonikelope.coronahmac -DartifactId=coronahmac -Dversion=x.x.x -Dpackaging=jar
```
##### What is CoronaHMAC?
It is a small module that is loaded at game startup to mitigate any player (including the server) trying to cheat using a hacked version of the game. For obvious reasons the source code is not available (if for any reason you are not comfortable using closed source dependencies, you can build coronapoker without this module).

#### Step 2 (build):
```
mvn clean install
```
<i>Advice: if you plan to distribute CoronaPoker as a package for your favorite Linux distribution, it is HIGHLY RECOMMENDED that you download from here the compiled jar or build it from the source code but without including the CoronaHMAC dependency (otherwise, every user built "binary" would be different generating false positives in the CoronaHMAC anti-cheating module).</i>


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



