package werewolves.connect;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Game implements AsyncResponse{
    private volatile boolean waitingForButton = false;
    private volatile boolean error = false;
    private String clickedCard = "card0";
    public static final String COM_SPLITTER = String.valueOf( ( char )28 );
    public final static String MSG_SPLITTER = String.valueOf( ( char )29 );
    public final static String UNIQUE_CHAR = String.valueOf( ( char )2 );
    public final static int MAX_ROLE_TIME = 30;
    private boolean minionWinsWhenHeDies = true;
    public Vector< String > players = new Vector<>();
    private String card;
    public String displayedCard;        // When you are copycat or paranormal then its value is different than card line above
    //private GameWindow gameWindow;
    public String nickname;
    public String gameID;
    public String[] statements = new String[ 50 ];

    private int port = 23000;
    private String ip = "185.20.175.81";
    public BufferedReader input;
    public PrintWriter output;
    private BlockingQueue< String > msgQueue = new LinkedBlockingQueue<>();
    private ConnectActivity connectActivity;

    Game( String nickname, String gameID, ConnectActivity cA ){
        this.connectActivity = cA;
        this.nickname = nickname;
        this.gameID = gameID;
        Connect connect = new Connect();
        connect.delegate = this;
        connect.execute();
    }

    @Override
    public void onConnected( boolean connected ){
        connectActivity.connected( connected );
        if( !connected )
            return;
        // Things after connected to the server
        new GameNet().executeOnExecutor( AsyncTask.THREAD_POOL_EXECUTOR );
        new GameLogic().executeOnExecutor( AsyncTask.THREAD_POOL_EXECUTOR );
    }

    private class Connect extends AsyncTask< Void, Void, String >{
        Socket socket;
        public AsyncResponse delegate = null;

        @Override
        protected void onPreExecute(){
            super.onPreExecute();
            connectActivity.connecting();
        }

        @Override
        protected String doInBackground( Void... voids ){
            try{
                socket = new Socket();
                socket.connect( new InetSocketAddress( ip, port ), 5000 );
                input = new BufferedReader( new InputStreamReader( socket.getInputStream(), Charset.forName( "UTF-8" ) ) );
                output = new PrintWriter( new OutputStreamWriter( socket.getOutputStream(), Charset.forName( "UTF-8" ) ), true );
                sendToServer( gameID );
                if( !receive().equals( "GOOD" ) )
                    return "No such game. Make sure you have correct id.";
                sendMsg( nickname );
                String nickInfo = receive();
                if( nickInfo.equals( "WRONGNICK" ) )
                    return "Nickname already taken.";
                if( !nickInfo.equals( "OK" ) )
                    return "Something went wrong.";
            } catch( IOException e ){
                return "Cannot connect to the server.";
            }
            return "Game will start soon. Please don't close this window.";
        }

        @Override
        protected void onPostExecute( String result ) {
            super.onPostExecute( result );
            connectActivity.info( result );
            boolean connected = result.startsWith( "Game will" );
            if( !connected ){
                try{
                    socket.close();
                } catch( IOException ignored ){}
            }
            delegate.onConnected( connected );
        }
    }

    private class GameNet extends AsyncTask< Void, Void, Void >{
        @Override
        protected Void doInBackground( Void... voids ){
            while( true ){
                try{
                    String msg = input.readLine();
                    if( msg == null || msg.equals( "ENDGAME" ) ){
                        msg = UNIQUE_CHAR + "ABORT";
                        msgQueue.put( msg );
                        return null;
                    }
                    msgQueue.put( msg );
                } catch( IOException | InterruptedException ignored ){}
            }
        }
    }

    private class GameLogic extends AsyncTask< Void, Void, Void >{
        @Override
        protected Void doInBackground( Void... voids ){
            getPlayers();
            getCard();
            return null;
        }

        @Override
        protected void onPostExecute( Void v ) {
            super.onPostExecute( v );
            connectActivity.started();
            connectActivity.info( "Game started - " + card );
        }
    }

    private void getPlayers(){
        String msg = read();
        String[] playersTab = msg.split( MSG_SPLITTER, 0 );
        players.addAll( Arrays.asList( playersTab ) );
    }

    private String getRandomPlayerCard(){
        int rand = new Random().nextInt( players.size() - 1 );
        if( rand == players.indexOf( nickname ) ) rand = players.size() - 1;
        return players.get( rand );
    }

    private void getCard(){
        card = read();
    }

    public void sendToServer( String msg ){
        output.println( msg );
    }

    public void sendMsg( String str ){
        output.println( "ADM" + COM_SPLITTER + str );
    }

    String receive(){
        try{
            return input.readLine();
        }catch( IOException e ){
            return "";
        }
    }

    String read(){
        try{
            return msgQueue.take();
        } catch( InterruptedException e ){
            return "";
        }
    }

//
//    public void gameLogic(){
//        Thread gameLogic = new Thread( () -> {
//            while( true ){
//                String msg = receive();
//                if( msg.equals( "WakeUp" ) ){
//                    wakeUp();
//                    break;
//                }
//                gameWindow.setStatementLabel( msg.charAt( 0) + msg.substring( 1 ).toLowerCase() + " " + statements[ 0 ] );
//                if( msg.equals( card.split( "_" )[ 0 ].toUpperCase() ) || ( msg.equals( "WEREWOLF" ) && card.equals( "Mystic wolf" ) ) ){
//                    gameWindow.setStatementLabel( msg.charAt( 0) + msg.substring( 1 ).toLowerCase() + " " + statements[ 1 ] );
//                    try {
//                        proceedCard( msg.charAt( 0) + msg.substring( 1 ).toLowerCase() );
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//                if( msg.equals( "THING" ) )
//                    waitForTingsTouch();
//            }
//            if( receive().equals( UNIQUE_CHAR + "VOTE" ) ){
//                gameWindow.setStatementLabel( statements[ 2 ] );
//                while( vote() != 0 );
//            }
//        } );
//        gameLogic.start();
//    }
//
//    private void proceedCard( String card ) throws InterruptedException {
//        try{
//            roleSignal.seek( Duration.ZERO );
//            roleSignal.play();
//        }
//        catch( NullPointerException ignored ){}
//
//        switch( card.split( "_" )[ 0 ] ){
//            case "Mystic wolf": makeMysticWolf(); break;
//            case "Minion": makeMinion(); break;
//            case "Copycat": makeCopycat(); break;
//            case "Insomniac": makeInsomniac(); break;
//            case "Werewolf": makeWerewolf(); break;
//            case "Witch": makeWitch(); break;
//            case "Beholder": makeBeholder(); break;
//            case "Seer": makeSeer(); break;
//            case "Thing": makeThing(); break;
//            case "Paranormal investigator": makeParanormal(); break;
//            case "Robber": makeRobber(); break;
//            case "Troublemaker": makeTroublemaker(); break;
//            case "Apprentice seer": makeApprenticeSeer(); break;
//        }
//    }
//
//    void makeCopycat(){
//        gameWindow.setRoleInfo( statements[ 16 ] );
//        waitingForButton = true;
//        gameWindow.setTableCardsActive( true );
//
//        // Waiting for clicked card, but with time limit of 30 seconds
//        long start = System.currentTimeMillis();
//        while( waitingForButton && System.currentTimeMillis() - start < MAX_ROLE_TIME * 1000 );
//        // If time is up, card will be selected randomly
//        if( waitingForButton ){
//            int rand = new Random().nextInt( 3 );
//            clickedCard = UNIQUE_CHAR + "card" + rand;
//            gameWindow.setRoleInfo( statements[ 13 ] + "\n" +
//                    statements[ 17 ] );
//            waitingForButton = false;
//        }
//        else
//            gameWindow.setRoleInfo( statements[ 17 ] );
//        gameWindow.setTableCardsActive( false );
//        gameWindow.setTableCardsSelected( false );
//        sendMsg( clickedCard );
//        card = receive();
//        gameWindow.setStatementLabel( statements[ 3 ] + " " + card.split( "_" )[ 0 ] );
//        gameWindow.reverseCard( clickedCard, card );
//        gameWindow.setCardLabel( " -> " + card.split( "_" )[ 0 ] );
//    }
//
//    void makeWerewolf(){
//        StringBuilder str = new StringBuilder();
//        String[] werewolves = receive().split( MSG_SPLITTER );
//        for( String werewolf: werewolves ){
//            if( !werewolf.equals( nickname ) ){
//                gameWindow.reverseCard( werewolf, "Werewolf_0" );
//                str.append( " " ).append( werewolf );
//            }
//        }
//        if( str.toString().isEmpty() ){
//            gameWindow.setRoleInfo( statements[ 18 ] );
//            waitingForButton = true;
//            gameWindow.setTableCardsActive( true );
//            long start1 = System.currentTimeMillis();
//            while( waitingForButton && System.currentTimeMillis() - start1 < MAX_ROLE_TIME * 1000 );
//            if( waitingForButton ){
//                int rand = new Random().nextInt( 3 );
//                clickedCard = UNIQUE_CHAR + "card" + rand;
//                gameWindow.setRoleInfo( statements[ 13 ] );
//                waitingForButton = false;
//            }
//            gameWindow.setTableCardsActive( false );
//            gameWindow.setTableCardsSelected( false );
//            sendMsg( clickedCard );
//            String chosenCard = receive();
//            gameWindow.reverseCard( clickedCard, chosenCard );
//        }
//        else
//            gameWindow.setRoleInfo( statements[ 19 ] + str.toString() + "." );
//    }
//
//    void makeMinion(){
//        StringBuilder str = new StringBuilder();
//        String[] werewolves = receive().split( MSG_SPLITTER, 0 );
//        if( !werewolves[ 0 ].equals( "" ) ){
//            for( String werewolf : werewolves ){
//                gameWindow.reverseCard( werewolf, "Werewolf_0" );
//                str.append( " " ).append( werewolf );
//            }
//        }
//        if( str.toString().isEmpty() )
//            gameWindow.setRoleInfo( statements[ 20 ] );
//        else
//            gameWindow.setRoleInfo( statements[ 21 ] + str.toString() + "." );
//    }
//
//    void makeMysticWolf(){
//        gameWindow.setRoleInfo( statements[ 22 ] );
//        waitingForButton = true;
//        gameWindow.setTableCardsActive( true );
//
//        // Waiting for clicked card, but with time limit of 30 seconds
//        long start1 = System.currentTimeMillis();
//        while( waitingForButton && System.currentTimeMillis() - start1 < MAX_ROLE_TIME * 1000 );
//        // If time is up, card will be selected randomly
//        if( waitingForButton ){
//            int rand = new Random().nextInt( 3 );
//            clickedCard = UNIQUE_CHAR + "card" + rand;
//            gameWindow.setRoleInfo( statements[ 13 ] );
//            waitingForButton = false;
//        }
//        gameWindow.setTableCardsActive( false );
//        gameWindow.setTableCardsSelected( false );
//        sendMsg( clickedCard );
//        String chosenCard = receive();
//        gameWindow.reverseCard( clickedCard, chosenCard );
//    }
//    void makeApprenticeSeer(){
//        gameWindow.setRoleInfo( statements[ 22 ] );
//        waitingForButton = true;
//        gameWindow.setTableCardsActive( true );
//
//        // Waiting for clicked card, but with time limit of 30 seconds
//        long start1 = System.currentTimeMillis();
//        while( waitingForButton && System.currentTimeMillis() - start1 < MAX_ROLE_TIME * 1000 );
//        // If time is up, card will be selected randomly
//        if( waitingForButton ){
//            int rand = new Random().nextInt( 3 );
//            clickedCard = UNIQUE_CHAR + "card" + rand;
//            gameWindow.setRoleInfo( statements[ 13 ] );
//            waitingForButton = false;
//        }
//        gameWindow.setTableCardsActive( false );
//        gameWindow.setTableCardsSelected( false );
//        sendMsg( clickedCard );
//        String chosenCard = receive();
//        gameWindow.reverseCard( clickedCard, chosenCard );
//    }
//    void makeWitch(){
//        gameWindow.setRoleInfo( statements[ 23 ] );
//        waitingForButton = true;
//        gameWindow.setTableCardsActive( true );
//
//        // Waiting for clicked card, but with time limit of 30 seconds
//        long start = System.currentTimeMillis();
//        while( waitingForButton && System.currentTimeMillis() - start < MAX_ROLE_TIME * 1000 );
//        // If time is up, card will be selected randomly
//        if( waitingForButton ){
//            int rand = new Random().nextInt( 3 );
//            clickedCard = UNIQUE_CHAR + "card" + rand;
//            gameWindow.setRoleInfo( statements[ 13 ] + "\n" + statements[ 24 ] );
//            waitingForButton = false;
//        }
//        gameWindow.setTableCardsActive( false );
//        gameWindow.setTableCardsSelected( false );
//        sendMsg( clickedCard );
//        String chosenCard = receive();
//        gameWindow.reverseCard( clickedCard, chosenCard );
//        String firstClickedCard = clickedCard;
//
//        waitingForButton = true;
//        gameWindow.setPlayersCardsActive( true );
//        start = System.currentTimeMillis();
//        while( waitingForButton && System.currentTimeMillis() - start < MAX_ROLE_TIME * 1000 );
//        if( waitingForButton ){
//            clickedCard = getRandomPlayerCard();
//            gameWindow.setRoleInfo( statements[ 13 ] );
//            waitingForButton = false;
//        }
//        gameWindow.setPlayersCardsActive( false );
//        gameWindow.setPlayersCardsSelected( false );
//        gameWindow.hideCenterCard( firstClickedCard );
//        gameWindow.reverseCard( clickedCard, chosenCard );
//        sendMsg( clickedCard );
//    }
//
//    void makeTroublemaker(){
//        gameWindow.setRoleInfo( statements[ 25 ] );
//        waitingForButton = true;
//        gameWindow.setPlayersCardsActive( true );
//
//        // Waiting for clicked card, but with time limit of 30 seconds
//        long start = System.currentTimeMillis();
//        while( waitingForButton && System.currentTimeMillis() - start < MAX_ROLE_TIME * 1000 );
//        // If time is up, card will be selected randomly
//        if( waitingForButton ){
//            clickedCard = getRandomPlayerCard();
//            gameWindow.setRoleInfo( statements[ 13 ] + "\n" + statements[ 26 ] );
//            waitingForButton = false;
//        }
//        String cards = clickedCard + MSG_SPLITTER;
//        gameWindow.setPlayerCardActive( players.indexOf( clickedCard ), false );
//        waitingForButton = true;
//        start = System.currentTimeMillis();
//        while( waitingForButton && System.currentTimeMillis() - start < MAX_ROLE_TIME * 1000 );
//        if( waitingForButton ){
//            clickedCard = getRandomPlayerCard();
//            gameWindow.setRoleInfo( statements[ 13 ] );
//            waitingForButton = false;
//        }
//        cards += clickedCard;
//        gameWindow.setPlayersCardsActive( false );
//        gameWindow.setPlayersCardsSelected( false );
//        sendMsg( cards );
//    }
//
//    void makeBeholder(){
//        gameWindow.setRoleInfo( statements[ 32 ] );
//        String msg = receive();
//        if( msg.equals( "NoSeer" ) ) gameWindow.setRoleInfo( statements[ 33 ] );
//        else gameWindow.reverseCard(msg,"Seer");
//    }
//
//    void makeSeer() throws InterruptedException {
//        gameWindow.setRoleInfo( statements[ 31 ] );
//        waitingForButton = true;
//        gameWindow.setTableCardsActive( true );
//
//        // Waiting for clicked card, but with time limit of 30 seconds
//        long start = System.currentTimeMillis();
//        while( waitingForButton && System.currentTimeMillis() - start < MAX_ROLE_TIME * 1000 ) ;
//        // If time is up, card will be selected randomly
//        if( waitingForButton ){
//            int rand = new Random().nextInt( 3 );
//            clickedCard = UNIQUE_CHAR + "card" + rand;
//            waitingForButton = false;
//        }
//        String cards = clickedCard + MSG_SPLITTER;
//        gameWindow.setCenterCardSelected( clickedCard, false );
//        waitingForButton = true;
//        while( waitingForButton && System.currentTimeMillis() - start < MAX_ROLE_TIME * 1000 ) ;
//        if( waitingForButton ){
//            int rand = new Random().nextInt( 3 );
//            clickedCard = UNIQUE_CHAR + "card" + rand;
//            waitingForButton = false;
//        }
//        cards += clickedCard;
//        sendMsg( cards );
//        String[] cardsInCenter = receive().split( MSG_SPLITTER );
//        String[] clickedCards = cards.split( MSG_SPLITTER );
//        gameWindow.setCenterCardSelected( clickedCard, false );
//        gameWindow.setTableCardsActive( false );
//        gameWindow.setTableCardsSelected( false );
//        gameWindow.reverseCard( clickedCards[ 0 ], cardsInCenter[ 0 ] );
//        gameWindow.reverseCard( clickedCards[ 1 ], cardsInCenter[ 1 ] );
//    }
//    void makeInsomniac(){
//        gameWindow.setRoleInfo( statements[ 28 ] );
//        String insomniacNow = receive();
//        gameWindow.setCardLabel( " -> " + insomniacNow );
//        gameWindow.updateMyCard( insomniacNow );
//    }
//
//    void makeParanormal(){
//        gameWindow.setRoleInfo( statements[ 27 ] );
//        gameWindow.setPlayersCardsActive( true );
//        for( int i = 0; i < 2; ++i ){
//            waitingForButton = true;
//            long start = System.currentTimeMillis();
//            while( waitingForButton && System.currentTimeMillis() - start < MAX_ROLE_TIME * 1000 );
//            if( waitingForButton ){
//                clickedCard = getRandomPlayerCard();
//                gameWindow.setRoleInfo( statements[ 13 ] + "\n" + statements[ 34 ] );
//                waitingForButton = false;
//            }
//            gameWindow.setPlayersCardsSelected( false );
//            sendMsg( clickedCard );
//            String msg = receive();
//            gameWindow.reverseCard( clickedCard, msg );
//            msg = msg.split( "_" )[ 0 ];
//            if( msg.equals( "Tanner" ) || msg.equals( "Werewolf" ) || msg.equals( "Mystic wolf" ) ){
//                gameWindow.setCardLabel( " -> " + msg );
//                gameWindow.setStatementLabel( statements[ 3 ] + " " + msg );
//                break;
//            }
//        }
//        gameWindow.setPlayersCardsActive( false );
//    }
//
//    void makeRobber(){
//        gameWindow.setRoleInfo( statements[ 29 ] );
//        waitingForButton = true;
//        gameWindow.setPlayersCardsActive( true );
//        long start = System.currentTimeMillis();
//        while( waitingForButton && System.currentTimeMillis() - start < MAX_ROLE_TIME * 1000 );
//        if( waitingForButton ){
//            clickedCard = getRandomPlayerCard();
//            gameWindow.setRoleInfo( statements[ 13 ] );
//            waitingForButton = false;
//        }
//        gameWindow.setPlayersCardsActive( false );
//        gameWindow.setPlayersCardsSelected( false );
//        sendMsg( clickedCard );
//        String msg = receive();
//        String msg2 = msg.split( "_" )[ 0 ];
//        gameWindow.setCardLabel( " -> " + msg2 );
//        gameWindow.setStatementLabel( statements[ 3 ] + " " + msg2 );
//        gameWindow.reverseCard( clickedCard, displayedCard );
//        gameWindow.updateMyCard( msg );
//    }
//
//    void makeThing(){
//        gameWindow.setRoleInfo( statements[ 30 ] );
//        int myIndex = players.indexOf( nickname );
//        waitingForButton = true;
//        gameWindow.setPlayerCardActive( ( myIndex + 1 ) % players.size(), true );
//        if( myIndex == 0 )
//            gameWindow.setPlayerCardActive( players.size() - 1, true );
//        else
//            gameWindow.setPlayerCardActive( myIndex - 1, true );
//        long start = System.currentTimeMillis();
//        while( waitingForButton && System.currentTimeMillis() - start < MAX_ROLE_TIME * 1000 );
//        if( waitingForButton ){
//            clickedCard = players.get( ( myIndex + 1 ) % players.size() );
//            gameWindow.setRoleInfo( statements[ 13 ] );
//            waitingForButton = false;
//        }
//        gameWindow.setPlayersCardsActive( false );
//        gameWindow.setPlayersCardsSelected( false );
//        sendMsg( clickedCard );
//    }
//
//    void waitForTingsTouch(){
//        if( receive().equals( "TOUCH" ) ){
//            gameWindow.setCardLabel( " -> " + statements[ 4 ] );
//            gameWindow.setStatementLabel( statements[ 5 ] );
//        }
//    }
//
//    void wakeUp(){
//        gameWindow.setStatementLabel( statements[ 14 ] );
//        gameWindow.setRoleInfo( statements[ 15 ] );
//        try{
//            wakeUpSignal.play();
//        }
//        catch( NullPointerException ignored ){}
//    }
//
//    private int vote(){
//        gameWindow.setPlayersCardsActive( true );
//        gameWindow.setTableCardsActive( true );
//        waitingForButton = true;
//        AtomicBoolean voteNotEnded = new AtomicBoolean( true );
//        Thread votes = new Thread( () -> {
//            while( true ){
//                String vote = receive();
//                if( vote.equals( UNIQUE_CHAR + "VOTEEND" ) ){
//                    voteNotEnded.set( false );
//                    break;
//                }
//                Platform.runLater( () -> gameWindow.drawArrow( vote.split( MSG_SPLITTER )[ 0 ], vote.split( MSG_SPLITTER )[ 1 ] ) );
//            }
//        } );
//        votes.start();
//        while( waitingForButton && voteNotEnded.get() );
//        gameWindow.setPlayersCardsActive( false );
//        gameWindow.setPlayersCardsSelected( false );
//        gameWindow.setTableCardsActive( false );
//        gameWindow.setTableCardsSelected( false );
//        if( !waitingForButton ){
//            if( clickedCard.substring( 0, 1 ).equals( UNIQUE_CHAR ) )
//                clickedCard = UNIQUE_CHAR + "table";
//            sendMsg( clickedCard );
//        }
//        waitingForButton = false;
//        while( voteNotEnded.get() );
//        String voteResult = receive();
//        if( voteResult.equals( UNIQUE_CHAR + "VOTE" ) ){      // vote again
//            gameWindow.setStatementLabel( statements[ 6 ] );
//            Thread t = new Thread( () -> Platform.runLater( () -> gameWindow.clearArrows() ) );
//            t.start();
//            return -1;
//        }
//        Vector< String > cardsNow = new Vector<>( Arrays.asList( receive().split( MSG_SPLITTER ) ) );
//        Vector< String > realCardsNow = new Vector<>( Arrays.asList( receive().split( MSG_SPLITTER ) ) );
//        for( int i = 0; i < players.size(); ++i ){
//            if( players.get( i ).equals( nickname ) )
//                gameWindow.updateMyCard( cardsNow.get( i ) );
//            else
//                gameWindow.reverseCard( players.get( i ), cardsNow.get( i ) );
//        }
//        for( int i = players.size(), j = 0; i < cardsNow.size(); ++i, ++j )
//            gameWindow.reverseCard( UNIQUE_CHAR + "card" + j, cardsNow.get( i ) );
//        int winner = whoWins( voteResult, realCardsNow );       // 9-tanner, 10-miasto, 11/12-wilkołaki/+minion
//        if( voteResult.equals( UNIQUE_CHAR + "table" ) ){
//            gameWindow.setStatementLabel( statements[ 35 ] + " - " + statements[ winner ] + "." );
//        }
//        else if( voteResult.equals( nickname ) )
//            gameWindow.setStatementLabel( statements[ 7 ] + " - " + statements[ winner ] + "." );
//        else
//            gameWindow.setStatementLabel( voteResult + " " + statements[ 8 ] + " - " + statements[ winner ] + "." );
//        gameWindow.quitButton.setDisable( false );
//        switch( winner ){
//            case 10: gameWindow.playMedia( "video/cityWins.mp4" ); break;
//            case 11: case 12: gameWindow.playMedia( "video/werewolvesWin.mp4" ); break;
//            case 9: gameWindow.playMedia( "video/tannerWins.mp4" ); break;
//        }
//        return 0;
//    }
//
//    private int whoWins( String player, Vector< String > cardsNow ){
//        if( player.equals( UNIQUE_CHAR + "table" ) ){
//            if( cardsNow.contains( "Werewolf_0" ) || cardsNow.contains( "Werewolf_1" ) ||
//                    cardsNow.contains( "Werewolf_2" ) || cardsNow.contains( "Mystic wolf" ) )
//                return 12;
//            else
//                return 10;
//        }
//        if( cardsNow.get( players.indexOf( player ) ).equals( "Tanner" ) )
//            return 9;
//        if( cardsNow.get( players.indexOf( player ) ).split( "_" )[ 0 ].equals( "Werewolf" ) ||
//                cardsNow.get( players.indexOf( player ) ).equals( "Mystic wolf" ) )
//            return 10;
//        else{
//            if( cardsNow.get( players.indexOf( player ) ).equals( "Minion" ) && !minionWinsWhenHeDies )
//                return 11;
//            else
//                return 12;
//        }
//    }
//
//    private void gameWindow( Window connectWindow ) throws IOException{
//        FXMLLoader fxmlLoader = new FXMLLoader( getClass().getResource( "fxml/gameWindow.fxml" ) );
//        Stage stage = new Stage();
//        stage.setTitle( "Werewolves" );
//        stage.getIcons().add( new Image( this.getClass().getResourceAsStream( "/img/icon.png" ) ) );
//        stage.setScene( new Scene( fxmlLoader.load(), 1280, 820 ) );
//        stage.initStyle( StageStyle.TRANSPARENT);
//        connectWindow.hide();
//        stage.show();
//        stage.getScene().getWindow().addEventFilter( WindowEvent.WINDOW_CLOSE_REQUEST, this::quit );
//        gameWindow = fxmlLoader.getController();
//        gameWindow.setCardLabel( card.split( "_" )[ 0 ] );
//        gameWindow.setGame( this );
//        gameWindow.createPlayersCards();
//        gameWindow.setNicknameLabel( nickname );
//        gameWindow.updateMyCard( card );
//        gameLogic();
//    }
//
//
//    private < T extends Event > void quit( T t ){
//        quit();
//    }
//
//    private void quit(){
//        Platform.exit();
//        System.exit( 0 );
//    }
//
//    private void restart(){
//        try{
//            Runtime.getRuntime().exec( "Werewolves.exe" );
//        } catch( IOException ignored ){}
//        quit();
//    }
//
//    public void setWaitingForButton( boolean value ){ waitingForButton = value; }
//    public void setClickedCard( String card ){ clickedCard = card; }
//
//    private void error( String error ){
//        Alert alert = new Alert( Alert.AlertType.ERROR );
//        alert.setTitle( "Error" );
//        alert.setContentText( error );
//        alert.showAndWait();
//        this.error = false;
//        restart();
//    }
}
