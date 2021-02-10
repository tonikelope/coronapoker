/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.tonikelope.coronapoker;

import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author tonikelope
 */
public class Translator {

    private static volatile String LANG = "";

    private static final ConcurrentHashMap<String, String> _rosetta = new ConcurrentHashMap();

    private static final ConcurrentHashMap<String, String> _attesor = new ConcurrentHashMap();

    private static void English() {

        String[][] rosetta = {
            {"Aviso: la privacidad del CHAT no está garantizada al usar la función de voz TTS (click para más info).", "Warning: CHAT privacy is not guaranteed when using the TTS voice function (click for more info)."},
            {"Aunque CoronaPoker usa cifrado extremo a extremo en todas las comunicaciones, el chat de\nvoz utiliza APIs externas TTS para convertir el texto en audio, por lo que los mensajes\nenviados a esos servidores podrían ser (en teoría) leidos por terceros.\n\nPOR FAVOR, TENLO EN CUENTA A LA HORA DE USAR EL CHAT", "Although CoronaPoker uses end-to-end encryption on all communications, the voice chat\nuses external TTS APIs to convert text to audio, so messages sent to those servers could\n(in theory) be read by third parties.\n\n PLEASE KEEP THIS IN MIND WHEN USING THE CHAT"},
            {"RECOMPRAR", "REBUY"},
            {"Aceptar", "OK"},
            {"Cancelar", "Cancel"},
            {"Recomprar (siguiente mano)", "Rebuy (next hand)"},
            {"¿ELIMINAR ESTA TIMBA?", "REMOVE THIS GAME?"},
            {"ELIMINAR TIMBA", "REMOVE GAME"},
            {"¿IGNORAR LOS MENSAJES TTS DE ESTE USUARIO?", "IGNORE TTS MESSAGES FROM THIS USER?"},
            {"La canción que suena en la sala de espera es \"The Dream\" compuesta por Jerry Goldsmith para la película Total Recall.", "The song that sounds in the waiting room is \"The Dream\" composed by Jerry Goldsmith for the movie Total Recall."},
            {"ERROR FATAL: NO SE HA PODIDO RECUPERAR LA CLAVE DE LA PERMUTACIÓN", "FATAL ERROR: UNABLE TO RECOVER THE PERMUTATION KEY"},
            {"¡¡TEN CUIDADO!! EL JUGADOR NO HIZO ESO LA OTRA VEZ. (ALGUIEN ESTÁ HACIENDO TRAMPAS).", "BE CAREFUL! THE PLAYER DIDN'T DO THAT LAST TIME. (HE/SHE'S OR THE SERVER IS CHEATING)."},
            {"NO SE PUEDE RECUPERAR LA MANO EN CURSO PORQUE FALTAN JUGADORES DE LA MANO ANTERIOR", "THE CURRENT HAND CANNOT BE RECOVERED BECAUSE PLAYERS FROM THE PREVIOUS HAND ARE MISSING"},
            {"Manos:", "Hands:"},
            {"CONTINUAR TIMBA", "RESUME GAME"},
            {"Nota: lo que se muestra es el porcentaje de manos subidas en relación a las manos jugadas.", "Note: what is shown is the percentage of hands raised in relation to the hands played."},
            {"Intercambio de claves...", "Keys exchange..."},
            {"Chequeo de integridad...", "Integrity check..."},
            {"PROBABLEMENTE HACIENDO TRAMPAS", "PROBABLY CHEATING"},
            {"¡¡TEN CUIDADO!! ES MUY PROBABLE QUE EL SERVIDOR ESTÉ INTENTANDO HACER TRAMPAS.", "BE CAREFUL! IT IS VERY LIKELY THAT THE SERVER IS TRYING TO CHEAT."},
            {"ESTE SERVIDOR NO ES SEGURO", "UNSECURE SERVER"},
            {"EL EJECUTABLE DE TU COPIA DEL JUEGO NO COINCIDE CON EL DEL SERVIDOR\n\n(Cuando esto pasa es porque alguna de las partes intenta hacer trampas con una versión hackeada del juego).", "THE BINARY OF YOUR COPY OF THE GAME DOES NOT MATCH WITH THAT OF THE SERVER\n\n(When this happens it is because one of the parties tries to cheat with a hacked version of the game)."},
            {"PAGAR", "PAY"},
            {"Nota: EFECTIVIDAD = (ROI / MANOS_JUGADAS) si ROI >=0, si no, EFECTIVIDAD = (ROI x MANOS_JUGADAS) (la EFECTIVIDAD mínima es -1)", "Note: EFFECTIVENESS = (ROI / PLAYED_HANDS) if ROI >=0, otherwise EFFECTIVENESS = (ROI x PLAYED_HANDS) (minimum EFFECTIVENESS is -1)"},
            {"RENDIMIENTO DE LOS JUGADORES", "PLAYERS PERFORMANCE"},
            {"EFECTIVIDAD", "EFFECTIVENESS"},
            {"Nota: se muestran el balance de la timba al TERMINAR la mano actual.", "Note: what is shown is the overall balance after finishing the current hand."},
            {"% APUESTAS/SUBIDAS EN EL PREFLOP", "% PREFLOP BETS/RAISES"},
            {"% APUESTAS/SUBIDAS EN EL FLOP", "% FLOP BETS/RAISES"},
            {"% APUESTAS/SUBIDAS EN EL TURN", "% TURN BETS/RAISES"},
            {"% APUESTAS/SUBIDAS EN EL RIVER", "% RIVER BETS/RAISES"},
            {"MANOS_APUESTA_SUBE", "BET_RAISE_HANDS"},
            {"La canción que suena en el visor de estadísticas es el tema principal de la mítica película EL GOLPE.", "The song that sounds in the statistics viewer is the main theme of the epic film THE STING."},
            {"Nota: se muestran las 1000 mejores jugadas ganadoras", "Warning: TOP-100 winner hands are shown"},
            {"Duración:", "Duration:"},
            {"EFICIENCIA", "PERFORMANCE"},
            {"MANOS", "HANDS"},
            {"BENEFICIO", "PROFIT"},
            {"JUGADOR", "PLAYER"},
            {"CARTAS_RECIBIDAS", "HOLE_CARDS"},
            {"CARTAS_JUGADA", "HAND_CARDS"},
            {"GANA", "WIN"},
            {"JUGADA", "HAND_VALUE"},
            {"MANOS_JUGADAS", "PLAYED_HANDS"},
            {"MANOS_GANADAS", "WINNER_HANDS"},
            {"TIEMPO", "TIME"},
            {"TIMBA", "GAME"},
            {"PAGADO", "PAY"},
            {"SÍ", "YES"},
            {"Estadísticas", "Stats"},
            {"ESTADÍSTICAS", "STATS"},
            {"Lo que no son cuentas, son cuentos", "Stats"},
            {"TODAS LAS TIMBAS", "ALL GAMES"},
            {"TODAS LAS MANOS", "ALL HANDS"},
            {"GANANCIAS/PÉRDIDAS", "PROFIT/LOSS"},
            {"TIEMPO MEDIO DE RESPUESTA (SEGUNDOS)", "AVERAGE RESPONSE TIME (SECONDS)"},
            {"% MANOS JUGADAS/GANADAS", "% PLAYED/WINNER HANDS"},
            {"JUGADAS GANADORAS", "WINNER HANDS"},
            {"Inicio:", "Start:"},
            {"Fin:", "End:"},
            {"Manos:", "Hands:"},
            {"Tiempo de juego:", "Play time:"},
            {"Jugadores:", "Players:"},
            {"Compra:", "Buyin:"},
            {"Ciegas:", "Blinds:"},
            {"Doblar ciegas (min):", "Blinds double (min):"},
            {"Recomprar:", "Rebuy:"},
            {"Ciega pequeña:", "Small blind:"},
            {"Ciega grande:", "Big blind"},
            {"Cartas comunitarias:", "Community cards:"},
            {"Jugadores PREFLOP:", "PREFLOP players:"},
            {"Jugadores FLOP:", "FLOP players:"},
            {"Jugadores TURN:", "TURN players:"},
            {"Jugadores RIVER:", "RIVER players:"},
            {"BOTE:", "POT:"},
            {"ATAJOS", "SHORTCUTS"},
            {"PASAR/IR -> [ESPACIO]\n\nAPOSTAR -> [ENTER] (FLECHA ARRIBA/ABAJO PARA SUBIR/BAJAR APUESTA)\n\nALL IN -> [MAYUS + ENTER]\n\nNO IR -> [ESC]\n\nMOSTRAR CARTAS -> [ESPACIO]\n\nMENSAJE CHAT RÁPIDO -> [º]", "CHECK/CALL -> [SPACE]\n\nBET -> [ENTER] (UP/DOWN ARROW KEYS TO RAISE/LOWER BET)\n\nALL IN -> [SHIFT + ENTER]\n\nFOLD -> [ESC]\n\nSHOW CARDS -> [SPACE]\n\nFAST CHAT MESSAGE -> [º]"},
            {"NO SE PUEDE RECOMPRAR EN ESTA TIMBA", "REBUY IS NOT ALLOWED IN THIS GAME"},
            {"CLICK PARA RECOMPRAR", "CLICK TO REBUY"},
            {"¿RECOMPRAR EN LA PRÓXIMA MANO?", "REBUY IN NEXT HAND?"},
            {"YA TIENES UNA SOLICITUD DE RECOMPRA ACTIVA", "YOU ALREADY HAVE A REBUY REQUEST PENDING"},
            {"PARA RECOMPRAR DEBES TENER MENOS DE ", "TO REBUY YOU MUST HAVE LESS THAN "},
            {"NO PUEDES RECOMPRAR EN ESTE MOMENTO", "YOU CAN NOT REBUY AT THIS TIME"},
            {"¿GENERAR CONTRASEÑA NUEVA?", "GENERATE NEW PASSWORD?"},
            {"PASSWORD COPIADA EN EL PORTAPAPELES", "PASSWORD COPIED TO CLIPBOARD"},
            {"NUEVA PASSWORD COPIADA EN EL PORTAPAPELES", "NEW PASSWORD COPIED TO CLIPBOARD"},
            {"PAUSA PROGRAMADA PARA TU PRÓXIMO TURNO", "PAUSE SCHEDULED FOR YOUR NEXT TURN"},
            {"PASSWORD INCORRECTA", "BAD PASSWORD"},
            {"LA TIMBA HA TERMINADO", "THE GAME IS OVER"},
            {"ESTA ES LA ÚLTIMA MANO", "THIS IS THE LAST HAND"},
            {"¿PAUSAR AHORA MISMO?", "PAUSE NOW?"},
            {"¿ÚLTIMA MANO?", "LAST HAND?"},
            {"ÚLTIMA MANO", "LAST HAND"},
            {"VIDEOLLAMADA", "VIDEOCALL"},
            {"CERRAR", "CLOSE"},
            {"Monitorizando portapapeles...", "Monitoring clipboard..."},
            {"COMPARTIR CON EL RESTO DE JUGADORES", "SHARE WITH THE REST OF THE PLAYERS"},
            {"¿RECOMPRA? -> ", "REBUY? -> "},
            {"Añadir bot", "Add bot"},
            {"Los comentarios sonoros durante el juego no se traducirán (puedes desactivarlos cuando empiece la partida).", "In-game audio comments will not be translated (you can turn them off when the game starts)."},
            {"CREAR TIMBA", "CREATE GAME"},
            {"UNIRME A TIMBA", "JOIN GAME"},
            {"Krusty sabe lo que se hace", "Krusty knows what he's doing"},
            {"Puede contener lenguaje soez", "May contain foul language"},
            {"Contiene apuestas con dinero ficticio", "It contains bets with fictitious money"},
            {"Permite jugar online", "Allows you to play online"},
            {"Click para activar/desactivar el sonido", "Click to turn the sound on/off"},
            {"¿De dónde ha salido esto?", "Where did this come from?"},
            {"El videojuego de Texas hold 'em NL que nos merecemos, no el que necesitamos ¿o era al revés?", "The Texas hold 'em NL videogame we deserve, not the one we need, or was it the opposite?"},
            {"Gracias a todos los amigos que han colaborado en esta aventura, en especial a Pepsi por sus barajas y el", "Thanks to all the friends who have collaborated in this adventure, especially to Pepsi for their decks and the"},
            {"\"hilo fino\", a Pepillo por ese talento para cazar los bugs más raros y a mi madre... por todo lo demás.", "\"fine-grain\", to Pepillo for that talent for hunting the weirdest bugs and to my mother... for everything else."},
            {"(Todos los céntimos desaparecidos en las betas fueron para una buena causa).", "(All the missing cents in the betas were for a good cause)."},
            {"En memoria de todas las víctimas de la COVID-19", "In memory of all the victims of COVID-19"},
            {"Nota: si posees el copyright de esta música (o cualquier otro elemento) y no permites su utilización, escríbeme a -> tonikelope@gmail.com", "Note: if you own the copyright of this music (or any other item) and do not allow its use, please write to me at -> tonikelope@gmail.com"},
            {"Generador de jugadas", "Hand generator"},
            {"Jugada superior", "Top hand"},
            {"Jugada inferior", "Lower hand"},
            {"Nota: no olvides mapear el puerto en tu router si quieres compartir la timba por Internet", "Note: don't forget to map the port on your router if you want to share the game over the Internet"},
            {"Generador de números aleatorios:", "Random number generator:"},
            {"Ciegas:", "Blinds:"},
            {"Compra inicial:", "BUYIN:"},
            {"Permitir recomprar", "Allow rebuy"},
            {"Doblar ciegas (minutos):", "Double blinds (minutes):"},
            {"RECUPERAR ÚLTIMA TIMBA", "RECOVER LAST GAME"},
            {"¡VAMOS!", "GO!"},
            {"Seguro", "Safe"},
            {"Muy seguro", "Very safe"},
            {"LA TIMBA HA TERMINADO (NO QUEDAN JUGADORES)", "GAME IS OVER (NO PLAYERS LEFT)"},
            {"En el MODO RECUPERACIÓN se continuará la timba anterior desde donde se paró:\n\n1) Es OBLIGATORIO que los jugadores antiguos usen los MISMOS NICKS.\n\n2) Para poder continuar desde el PUNTO EXACTO (con la mismas cartas) es OBLIGATORIO que se conecten TODOS los jugadores antiguos.\nSi esto no es posible, se \"perderá\" la mano que estaba en curso cuando se interrumpió la timba.\n\n3) Está permitido que se unan a la timba jugadores nuevos (estarán la primera mano de espectadores).", "In the RECOVERY MODE the previous game will be continued from where it was stopped:\n\n1) It is MANDATORY that the old players use the SAME NICKS.\n\n2) In order to continue from the EXACT POINT (with the same cards) it is MANDATORY that ALL the old players connect.\nIf this is not possible, the hand that was in progress when the game was interrupted will be lost.\n\n3) New players are allowed to join the game (they will be the first hand as spectators)."},
            {"¡A LA PUTA CALLE!", "GET OUT OF HERE!"},
            {"EL SERVIDOR HA TERMINADO LA TIMBA", "THE SERVER HAS FINISHED THE GAME"},
            {"A ver, se acabó el tiempo para llorar. ¿TE REENGANCHAS O QUÉ?", "Time for crying is over. YOU REBUY OR WHAT?"},
            {"¡CUIDADO! ERES EL ANFITRIÓN Y SI SALES SE TERMINARÁ LA TIMBA. ¿ESTÁS SEGURO?", "BEWARE! YOU'RE THE HOST AND IF YOU COME OUT THE GAME WILL BE OVER. ARE YOU SURE?"},
            {"¡CUIDADO! Si sales de la timba no podrás volver a entrar. ¿ESTÁS SEGURO?", "CAUTION! If you get out of the game you won't be able to get back in. ARE YOU SURE?"},
            {"Este usuario tiene problemas de conexión que bloquean la partida. ¿Quieres expulsarlo?", "This user has connection problems that block the game. Do you want to kick him/her out?"},
            {"Hay jugadores de la timba anterior que no se han vuelto a conectar.\n(Si no se conectan no se podrá recuperar la última mano en curso).\n\n¿EMPEZAMOS YA?", "There are players from the previous game who have not reconnected.\n(If they do not connect, the last hand in progress cannot be retrieved.)\n\nSHALL WE START NOW?"},
            {"Te falta algún campo obligatorio por completar", "You are missing a required field"},
            {"Versión de CoronaPoker incorrecta", "Wrong CoronaPoker version"},
            {"Llegas TARDE. La partida ya ha empezado.", "You're LATE. The game has already started."},
            {"NO HAY SITIO", "THERE IS NO SEATS"},
            {"El nick elegido ya lo está usando otro usuario.", "The chosen nick is already being used by another user."},
            {"El servidor ha cancelado la timba antes de empezar.", "The server has canceled the game before it starts."},
            {"ERROR INESPERADO", "UNEXPECTED ERROR"},
            {"ALGO HA FALLADO. (Probablemente la timba no esté aún creada).", "SOMETHING HAS GONE WRONG. (Probably the game is not yet created)."},
            {"ALGO HA FALLADO. Has perdido la conexión con el servidor.", "SOMETHING HAS GONE WRONG. You've lost the connection to the server."},
            {"ALGO HA FALLADO. (Probablemente ya hay una timba creada en el mismo puerto).", "SOMETHING HAS GONE WRONG. (There's probably already a game created in the same port)."},
            {"Hay usuarios que están tardando demasiado en responder (se les eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?", "There are users who are taking too long to respond (they will be removed from the game). DO WE WAIT A LITTLE MORE?"},
            {" parece que perdió la conexión y no ha vuelto a conectar (se le eliminará de la timba). ¿ESPERAMOS UN POCO MÁS?", " it looks like he/she lost the connection and hasn't reconnected (he/she'll be removed from the game). DO WE WAIT A LITTLE MORE?"},
            {"Parece que hubo algún problema con RANDOM.ORG (se usará el SPRNG en su lugar)\n¿Quieres desactivar RANDOM.ORG para el resto de la partida?", "There seems to be some problem with RANDOM.ORG (the SPRNG will be used instead).\nDo you want to disable RANDOM.ORG for the rest of the game?"},
            {"PASAR", "CHECK"},
            {"IR", "CALL"},
            {"APOSTAR", "BET"},
            {"SUBIR", "RAISE"},
            {"RESUBIR", "RERAISE"},
            {"NO IR", "FOLD"},
            {"IR", "CALL"},
            {"HABLAS TÚ", "YOUR TURN"},
            {"Archivo", "File"},
            {"Ver chat (ALT+C)", "Show chat (ALT+C)"},
            {"Ver chat", "Show chat"},
            {"Ver registro (ALT+R)", "Show log (ALT+R)"},
            {"Ver registro", "Show log"},
            {"Generador de jugadas (ALT+J)", "Hand generator (ALT+J)"},
            {"Pausar timba (ALT+P)", "Pause game (ALT+P)"},
            {"Pausar timba", "Pause game"},
            {"PANTALLA COMPLETA (ALT+F)", "FULL SCREEN (ALT+F)"},
            {"PANTALLA COMPLETA", "FULL SCREEN"},
            {"Salir (ALT+F4)", "Exit (ALT+F4)"},
            {"Salir", "Exit"},
            {"SALIR", "EXIT"},
            {"Aumentar (CTRL++)", "Increase (CTRL++)"},
            {"Reducir (CTRL+-)", "Reduce (CTRL--)"},
            {"Aumentar zoom", "Increase zoom"},
            {"Reducir zoom", "Reduce zoom"},
            {"Vista compacta (ALT+X)", "Compact view (ALT+X)"},
            {"Vista compacta", "Compact view"},
            {"Preferencias", "Preferences"},
            {"SONIDOS (ALT+S)", "SOUNDS (ALT+S)"},
            {"SONIDOS", "SOUNDS"},
            {"Sonidos de coña", "Joke sounds"},
            {"Música ambiental", "Background music"},
            {"Confirmar todas las acciones", "Confirm every action"},
            {"Botones AUTO", "AUTO buttons"},
            {"Animación al repartir", "Dealer animation"},
            {"Mostrar reloj (ALT+W)", "Show clock (ALT+W)"},
            {"Mostrar reloj", "Show clock"},
            {"Barajas", "Decks"},
            {"Tapetes", "Mats"},
            {"Recompra automática", "Auto rebuy"},
            {"Ayuda", "Help"},
            {"Acerca de", "About"},
            {"Bote: ", "Pot: "},
            {"Mano: ", "Hand: "},
            {"Apuestas: ", "Bets: "},
            {"Ciegas: ", "Blinds: "},
            {"MOSTRAR", "SHOW"},
            {" MUESTRAS (", " SHOW ("},
            {" MUESTRA (", " SHOWS ("},
            {"PENSANDO", "THINKING"},
            {"AUDITOR DE CUENTAS", "ACCOUNTS AUDITOR"},
            {"¡OJO A ESTO: NO SALEN LAS CUENTAS GLOBALES! -> (STACKS + INDIVISIBLE) != BUYIN", "WATCH OUT FOR THIS: THE GLOBAL ACCOUNTS DON'T COME OUT! -> (STACKS + INDIVISIBLE) = BUYIN"},
            {"CALENTANDO", "WARMING UP"},
            {"ABANDONA LA TIMBA", "QUIT THE GAME"},
            {"ABANDONAS LA TIMBA", "QUIT THE GAME"},
            {" MUESTRA (", " SHOWS ("},
            {" se UNE a la TIMBA.", " JOINS THE GAME."},
            {"SE DOBLAN LAS CIEGAS", "THE BLINDS ARE DOUBLED"},
            {"MANO", "HAND"},
            {"BOTE SOBRANTE NO DIVISIBLE", "INDIVISIBLE POT"},
            {"RECUPERANDO TIMBA...", "RECOVERING GAME..."},
            {"MANO RECUPERADA", "RECOVERED HAND"},
            {"TIMBA RECUPERADA", "GAME RECOVERED"},
            {" es la CIEGA GRANDE (", " is the BIG BLIND ("},
            {" es la CIEGA PEQUEÑA (", " is the SMALL BLIND ("},
            {" es el DEALER", " is the DEALER"},
            {" GANA BOTE (", " WINS POT ("},
            {") SIN TENER QUE MOSTRAR", ") WITHOUT NEEDING TO SHOW"},
            {"GANAS SIN TENER QUE MOSTRAR", "YOU WIN WITHOUT NEEDING TO SHOW"},
            {"GANA SIN TENER QUE MOSTRAR", "WINS WITHOUT NEEDING TO SHOW"},
            {") GANA BOTE (", ") WINS POT ("},
            {" (---) PIERDE BOTE (", " (---) LOSES POT ("},
            {") GANA BOTE PRINCIPAL (", ") WINS MAIN POT ("},
            {" (---) PIERDE BOTE PRINCIPAL (", " (---) LOSES MAIN POT ("},
            {" RECUPERA BOTE (SOBRANTE) SECUNDARIO #", " RECOVERS SECONDARY POT #"},
            {") GANA BOTE SECUNDARIO #", ") WINS SECONDARY POT #"},
            {" (---) PIERDE BOTE SECUNDARIO #", " (---) LOSES SECONDARY POT #"},
            {" -> TE QUEDAS DE ESPECTADOR", " -> YOU STAY AS A SPECTATOR"},
            {" RECOMPRA (", " REBUY ("},
            {"\n*************** LA TIMBA HA TERMINADO ***************", "\n*************** GAME IS OVER ***************"},
            {"FIN DE LA TIMBA -> ", "END OF GAME -> "},
            {"COMIENZA LA TIMBA -> ", "GAME STARTS -> "},
            {" - REGISTRO DE LA TIMBA]", " - GAME LOG]"},
            {"REGISTRO DE LA TIMBA", "GAME LOG"},
            {"EMPEZAR YA", "START NOW"},
            {"Expulsar jugador", "Kick user"},
            {"Conectando...", "Connecting..."},
            {"Esperando jugadores...", "Waiting for players..."},
            {" - Sala de espera (", " - Waiting room ("},
            {"Sorteando sitios...", "Drawing places..."},
            {"Timba en curso", "Game in progress"},
            {" - Timba en curso (", " - Game in progress ("},
            {"Conectado", "Connected"},
            {"TIMBA PAUSADA", "GAME PAUSED"},
            {"CONTINUAR", "RESUME"},
            {"PAUSAR", "PAUSE"},
            {"RECUPERANDO TIMBA", "RECOVERING GAME"},
            {"POR FAVOR, ESPERA", "PLEASE WAIT"},
            {"REANUDANDO TIMBA...", "RESUMING GAME..."},
            {"GANA SIN MOSTRAR", "WINS WITHOUT SHOWING"},
            {"GANAS SIN MOSTRAR", "WIN WITHOUT SHOWING"},
            {"CONTINUAR", "CONTINUE"},
            {"Verde", "Green"},
            {"Azul", "Blue"},
            {"Rojo", "Red"},
            {"Sin tapete", "No mat"},
            {"AUTO PASAR +CG", "AUTO CHECK +BB"},
            {"AUTO NO IR", "AUTO FOLD"},
            {"Crear timba", "Create game"},
            {"Unirme a timba", "Join game"},
            {"Haz click para cambiar el avatar", "Click to change your avatar"},
            {"Se usará para barajar las cartas", "It will be used to shuffle the cards"},
            {"[10-100] ciegas grandes", "[10-100] big blinds"},
            {"Si algún jugador se queda sin fichas", "If any player runs out of chips"},
            {"El MODO RECUPERACIÓN permite arrancar una timba que se interrumpió previamente", "The RECOVERY MODE allows you to resume a game that was previously interrupted"},
            {"Cinemáticas", "Cinematics"},
            {"PIERDE ", "LOSES "},
            {"GANA ", "WINS "},
            {"PIERDE", "LOSES"},
            {"NI GANA NI PIERDE", "NO WIN, NO LOSS"},
            {"ESPECTADOR", "SPECTATOR"},
            {"] dice: ", "] says: "},
            {"Inicializando timba...", "Starting game..."},
            {"Cartas UTF-8", "UTF-8 cards"},
            {"El hilo musical que suena durante el juego fue compuesto por David Luong.", "The musical theme that sounds during the game was composed by David Luong."},
            {"La canción que suena aquí es \"La Sala del Trono\" compuesta por John Williams para Star Wars.", "The song playing here is \"The Throne Room\" composed by John Williams for Star Wars."}};

        for (var r : rosetta) {
            _rosetta.putIfAbsent(r[0], r[1]);
            _attesor.putIfAbsent(r[1], r[0]);
        }
    }

    public static String translate(String orig) {

        return _translate(orig, false);
    }

    public static String translate(String orig, boolean force) {

        return _translate(orig, force);
    }

    private static String _translate(String orig, boolean force) {

        if (!LANG.equals(GameFrame.LANGUAGE)) {

            LANG = GameFrame.LANGUAGE;

            _rosetta.clear();

            _attesor.clear();

            switch (LANG) {

                case "en":
                    English();
                    break;
                default:
                    English();
            }
        }

        if (LANG.equals(GameFrame.DEFAULT_LANGUAGE)) {

            if (orig != null && force) {

                String key = orig;

                String val = _attesor.get(key);

                return val != null ? val : orig;
            }

        } else {

            if (orig != null) {

                String key = orig;

                String val = _rosetta.get(key);

                return val != null ? val : orig;
            }

        }

        return orig;
    }

}
