<i>Once upon a time, before ChatGPT, some humans programmed for pleasure...</i>

<h1>CoronaPoker</h1>

[![Maintenance](https://img.shields.io/badge/Maintained%3F-yes-green.svg)](https://GitHub.com/Naereen/StrapDown.js/graphs/commit-activity) [![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

<p align="justify">This is the project of a perfectionist, who one day during the confinement of COVID19, came up with the idea of developing the most complete and fun open source game of Texas hold'em for his friends. I hope you enjoy playing it as much as I enjoy programming it. Carpe diem.</p>

<p align="center"><a href="https://github.com/tonikelope/coronapoker/releases/latest" target="_blank"><img src="https://raw.githubusercontent.com/tonikelope/megabasterd/master/src/main/resources/images/linux-mac-windows.png"></a></p><p align="center"><b>(Proudly) developed with:</b><br><img src="java_swing_mini.png" height="100"></p>

<h1 align="center"><a href="https://github.com/tonikelope/coronapoker/releases/latest"><b>DOWNLOAD CORONAPOKER</b></a></h1>

https://github.com/tonikelope/coronapoker/assets/1344008/88ee3491-459f-43e7-8f62-3567c593482d


## Some features:
- Cross platform.
- No central servers nor third parties logging things (just you and your friends).
- Point-to-point encryption (DH + AES 128).
- Password protected games.
- Secure by design with a (modest) anticheat module.
- Up to 10 simultaneous human/bot players.
- Intuitive interface (with comfortable key shortcuts).
- Global in-game zoom (UHD resolution supported).
- TRUE RANDOM shuffle (<a href="https://github.com/tonikelope/coronapoker/raw/master/shuffle.pdf">MORE INFO</a>).
- Cool sounds, decks, mats, cinematics and 3D card effects.
- ALL-IN side pots fully supported.
- "Dead button" rule for BB/SB/DE positions.
- IWTSTH rule available (can be enabled/disabled by the host during the game).
- RABBIT HUNTING (can be enabled/disabled by the host during the game).
- All blinds stuff adjustable by the host during the game.
- Rebuy available.
- Waiting room chat with emojis, custom gifs and urls support.
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

#### Step 1 (install CoronaHMAC as Maven local package):
```
mvn install:install-file -Dfile=coronaHMAC_x.y.jar -DgroupId=com.tonikelope.coronahmac -DartifactId=coronahmac -Dversion=x.y -Dpackaging=jar
```

#### Step 2 (build CoronaPoker):
```
mvn clean install
```

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

### EXTRA (AntiCheat) What is CoronaHMAC?: 
<p align="justify">CoronaHMAC is a small library that is loaded at game startup to mitigate any player trying to cheat using a different binary version of the game than other players. For security reasons the source code is not available (if for any reason you are not comfortable using closed source dependencies but you want to build CoronaPoker, go to OPTION C).

<p align="center"><img src="https://github.com/tonikelope/coronapoker/raw/master/coronahmac.png"></p>

1) CoronaPoker.jar is executed normally.
2) As soon as it starts, the WatchService API is called to start monitoring changes in the directory where CoronaPoker.jar is located.
3) CoronaPoker.jar is restarted but this time disabling the option to use agents for debugging as well with a tcp port so that the new process can communicate and authenticate with the old process.
4) The new CoronaPoker process sends to the old one a message authenticated with HMACSHA256 (with a pre-shared secret key that is obfuscated inside CoronaHMAC) that contains its PID concatenated with the random_nonce_1 sent by the original CoronaPoker process and a new random_nonce_2 generated from new CoronaPoker process at runtime.
5) The original CoronaPoker process verifies the message and responds to the new process by resending the message received back authenticated with HMACSHA256 with the pre-shared secret key.
6) Once mutually authenticated, new process calculates the HMACSHA256 (with the pre-shared secret key) of CoronaPoker.jar file that it will use as seed to authenticate and verify that the other players are using the same CoronaPoker.jar binary.
7) Once the CoronaPoker.jar HMACSHA256 has been calculated, the new process generates a random_nonce_3 and sends it to the old process to let it know that it has finished. 
8) The original process responds with a HMACSHA256 with pid+all random nonces generated during the process concatenated with a flag for any creation/delete/modification event detected in CoronaPoker.jar directory.
9) After verifying the response of the old process, CoronaHMAC starts the game.

<p align="justify">In addition to this system, CoronaPoker includes an feature that allows players to obtain at any time during the game a "snapshot" of any player's process list, as well as a screenshot of his screen, in case he/she is using any external application to cheat. (This feature can be activated or deactivated by the server when setting the game options).</p>

<p align="justify">Finally, CoronaPoker is designed in such a way that only the game server "knows" the cards dealt to the players and they will be sent to the rest of the players if necessary at the moment they are needed. In addition, for emergency recovery mode, the permutation of the current hand's deck is stored on the server hdd in encrypted form with a key that is sent to other players (and not stored by the server in any form).</p>
