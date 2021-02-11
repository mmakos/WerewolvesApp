package werewolves.connect;

import android.content.Intent;
import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Game implements AsyncResponse, Serializable{
    private final Object clickedLock = new Object();        // to synchronize clicked card moment
    private Boolean isClicked = false;
    private String clickedCard;
    public static final String COM_SPLITTER = String.valueOf( ( char )28 );
    public final static String MSG_SPLITTER = String.valueOf( ( char )29 );
    public final static String UNIQUE_CHAR = String.valueOf( ( char )2 );
    public final static int MAX_ROLE_TIME = 30;
    private static final boolean minionWinsWhenHeDies = true;
    public Vector< String > players = new Vector<>();
    private String card;
    public String displayedCard;        // When you are copycat or paranormal then its value is different than card line above
    public String nickname;
    public String gameID;
    public String[] statements = new String[ 50 ];

    private int port = 23000;
    private String ip = "185.20.175.81";
    public BufferedReader input;
    public PrintWriter output;
    private BlockingQueue< String > msgQueue = new LinkedBlockingQueue<>();
    private ConnectActivity connectActivity;
    private GameActivity gameActivity;

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
        Intent intent = new Intent( connectActivity, GameActivity.class );
        intent.putExtra( "gameObject", this );
        connectActivity.startActivity( intent );
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

    public void setGameActivity( GameActivity gameActivity ){
        this.gameActivity = gameActivity;
        gameActivity.setCardLabel( card.split( "_" )[ 0 ] );
        gameActivity.createPlayersCards();
        gameActivity.setNicknameLabel( nickname );
        gameActivity.updateMyCard( card );
        gameLogic();
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


    public void gameLogic(){
        Thread gameLogic = new Thread( () -> {
            while( true ){
                String msg = receive();
                if( msg.equals( "WakeUp" ) ){
                    wakeUp();
                    break;
                }
                gameActivity.setStatementLabel( msg.charAt( 0) + msg.substring( 1 ).toLowerCase() + " " + statements[ 0 ] );
                if( msg.equals( card.split( "_" )[ 0 ].toUpperCase() ) || ( msg.equals( "WEREWOLF" ) && card.equals( "Mystic wolf" ) ) ){
                    gameActivity.setStatementLabel( msg.charAt( 0) + msg.substring( 1 ).toLowerCase() + " " + statements[ 1 ] );
                    try {
                        proceedCard( msg.charAt( 0) + msg.substring( 1 ).toLowerCase() );
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if( msg.equals( "THING" ) )
                    waitForTingsTouch();
            }
            if( receive().equals( UNIQUE_CHAR + "VOTE" ) ){
                gameActivity.setStatementLabel( statements[ 2 ] );
                while( vote() != 0 );
            }
        } );
        gameLogic.start();
    }

    private void proceedCard( String card ) throws InterruptedException {
//        try{
//            roleSignal.seek( Duration.ZERO );
//            roleSignal.play();
//        }
//        catch( NullPointerException ignored ){}

        switch( card.split( "_" )[ 0 ] ){
            case "Mystic wolf": makeMysticWolf(); break;
            case "Minion": makeMinion(); break;
            case "Copycat": makeCopycat(); break;
            case "Insomniac": makeInsomniac(); break;
            case "Werewolf": makeWerewolf(); break;
            case "Witch": makeWitch(); break;
            case "Beholder": makeBeholder(); break;
            case "Seer": makeSeer(); break;
            case "Thing": makeThing(); break;
            case "Paranormal investigator": makeParanormal(); break;
            case "Robber": makeRobber(); break;
            case "Troublemaker": makeTroublemaker(); break;
            case "Apprentice seer": makeApprenticeSeer(); break;
        }
    }

    void makeCopycat(){
        gameActivity.setRoleInfo( statements[ 16 ] );
        gameActivity.setTableCardsActive( true );

        // Waiting for clicked card, but with time limit of 30 seconds
        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
            gameActivity.setRoleInfo( statements[ 13 ] + "\n" +
                    statements[ 17 ] );
        }
        else
            gameActivity.setRoleInfo( statements[ 17 ] );
        gameActivity.setTableCardsActive( false );
        sendMsg( cardClick );
        card = receive();
        gameActivity.setStatementLabel( statements[ 3 ] + " " + card.split( "_" )[ 0 ] );
        gameActivity.reverseCard( cardClick, card );
        gameActivity.setCardLabel( " -> " + card.split( "_" )[ 0 ] );
    }

    void makeWerewolf(){
        StringBuilder str = new StringBuilder();
        String[] werewolves = receive().split( MSG_SPLITTER );
        for( String werewolf: werewolves ){
            if( !werewolf.equals( nickname ) ){
                gameActivity.reverseCard( werewolf, "Werewolf_0" );
                str.append( " " ).append( werewolf );
            }
        }
        if( str.toString().isEmpty() ){
            gameActivity.setRoleInfo( statements[ 18 ] );
            gameActivity.setTableCardsActive( true );
            String cardClick = getClickedCard();
            if( cardClick == null ){
                int rand = new Random().nextInt( 3 );
                cardClick = UNIQUE_CHAR + "card" + rand;
                gameActivity.setRoleInfo( statements[ 13 ] );
            }
            gameActivity.setTableCardsActive( false );
            sendMsg( cardClick );
            String chosenCard = receive();
            gameActivity.reverseCard( cardClick, chosenCard );
        }
        else
            gameActivity.setRoleInfo( statements[ 19 ] + str.toString() + "." );
    }

    void makeMinion(){
        StringBuilder str = new StringBuilder();
        String[] werewolves = receive().split( MSG_SPLITTER, 0 );
        if( !werewolves[ 0 ].equals( "" ) ){
            for( String werewolf : werewolves ){
                gameActivity.reverseCard( werewolf, "Werewolf_0" );
                str.append( " " ).append( werewolf );
            }
        }
        if( str.toString().isEmpty() )
            gameActivity.setRoleInfo( statements[ 20 ] );
        else
            gameActivity.setRoleInfo( statements[ 21 ] + str.toString() + "." );
    }

    void makeMysticWolf(){
        gameActivity.setRoleInfo( statements[ 22 ] );
        gameActivity.setTableCardsActive( true );

        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
            gameActivity.setRoleInfo( statements[ 13 ] );
        }
        gameActivity.setTableCardsActive( false );
        sendMsg( cardClick );
        String chosenCard = receive();
        gameActivity.reverseCard( cardClick, chosenCard );
    }
    void makeApprenticeSeer(){
        gameActivity.setRoleInfo( statements[ 22 ] );
        gameActivity.setTableCardsActive( true );

        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
            gameActivity.setRoleInfo( statements[ 13 ] );
        }
        gameActivity.setTableCardsActive( false );
        sendMsg( cardClick );
        String chosenCard = receive();
        gameActivity.reverseCard( cardClick, chosenCard );
    }
    void makeWitch(){
        gameActivity.setRoleInfo( statements[ 23 ] );
        gameActivity.setTableCardsActive( true );

        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
            gameActivity.setRoleInfo( statements[ 13 ] + "\n" + statements[ 24 ] );
        }
        gameActivity.setTableCardsActive( false );
        sendMsg( cardClick );
        String chosenCard = receive();
        gameActivity.reverseCard( cardClick, chosenCard );
        String firstClickedCard = cardClick;

        gameActivity.setPlayersCardsActive( true );
        cardClick = getClickedCard();
        if( cardClick == null ){
            cardClick = getRandomPlayerCard();
            gameActivity.setRoleInfo( statements[ 13 ] );
        }
        gameActivity.setPlayersCardsActive( false );
        gameActivity.hideCenterCard( firstClickedCard );
        gameActivity.reverseCard( cardClick, chosenCard );
        sendMsg( cardClick );
    }

    void makeTroublemaker(){
        gameActivity.setRoleInfo( statements[ 25 ] );
        gameActivity.setPlayersCardsActive( true );

        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            cardClick = getRandomPlayerCard();
            gameActivity.setRoleInfo( statements[ 13 ] + "\n" + statements[ 26 ] );
        }
        String cards = cardClick + MSG_SPLITTER;
        gameActivity.setPlayerCardActive( players.indexOf( cardClick ), false );
        cardClick = getClickedCard();
        if( cardClick == null ){
            cardClick = getRandomPlayerCard();
            gameActivity.setRoleInfo( statements[ 13 ] );
        }
        cards += cardClick;
        gameActivity.setPlayersCardsActive( false );
        sendMsg( cards );
    }

    void makeBeholder(){
        gameActivity.setRoleInfo( statements[ 32 ] );
        String msg = receive();
        if( msg.equals( "NoSeer" ) ) gameActivity.setRoleInfo( statements[ 33 ] );
        else gameActivity.reverseCard(msg,"Seer");
    }

    void makeSeer() throws InterruptedException {
        gameActivity.setRoleInfo( statements[ 31 ] );
        gameActivity.setTableCardsActive( true );

        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
        }
        String cards = cardClick + MSG_SPLITTER;
        cardClick = getClickedCard();
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
        }
        cards += cardClick;
        sendMsg( cards );
        String[] cardsInCenter = receive().split( MSG_SPLITTER );
        String[] clickedCards = cards.split( MSG_SPLITTER );
        gameActivity.setTableCardsActive( false );
        gameActivity.reverseCard( clickedCards[ 0 ], cardsInCenter[ 0 ] );
        gameActivity.reverseCard( clickedCards[ 1 ], cardsInCenter[ 1 ] );
    }
    void makeInsomniac(){
        gameActivity.setRoleInfo( statements[ 28 ] );
        String insomniacNow = receive();
        gameActivity.setCardLabel( " -> " + insomniacNow );
        gameActivity.updateMyCard( insomniacNow );
    }

    void makeParanormal(){
        gameActivity.setRoleInfo( statements[ 27 ] );
        gameActivity.setPlayersCardsActive( true );
        for( int i = 0; i < 2; ++i ){
            String cardClick = getClickedCard();
            if( cardClick == null ){
                cardClick = getRandomPlayerCard();
                gameActivity.setRoleInfo( statements[ 13 ] + "\n" + statements[ 34 ] );
            }
            sendMsg( cardClick );
            String msg = receive();
            gameActivity.reverseCard( cardClick, msg );
            msg = msg.split( "_" )[ 0 ];
            if( msg.equals( "Tanner" ) || msg.equals( "Werewolf" ) || msg.equals( "Mystic wolf" ) ){
                gameActivity.setCardLabel( " -> " + msg );
                gameActivity.setStatementLabel( statements[ 3 ] + " " + msg );
                break;
            }
        }
        gameActivity.setPlayersCardsActive( false );
    }

    void makeRobber(){
        gameActivity.setRoleInfo( statements[ 29 ] );
        gameActivity.setPlayersCardsActive( true );
        String cardClick = getClickedCard();
        if( cardClick == null ){
            cardClick = getRandomPlayerCard();
            gameActivity.setRoleInfo( statements[ 13 ] );
        }
        gameActivity.setPlayersCardsActive( false );
        sendMsg( cardClick );
        String msg = receive();
        String msg2 = msg.split( "_" )[ 0 ];
        gameActivity.setCardLabel( " -> " + msg2 );
        gameActivity.setStatementLabel( statements[ 3 ] + " " + msg2 );
        gameActivity.reverseCard( cardClick, displayedCard );
        gameActivity.updateMyCard( msg );
    }

    void makeThing(){
        gameActivity.setRoleInfo( statements[ 30 ] );
        int myIndex = players.indexOf( nickname );
        gameActivity.setPlayerCardActive( ( myIndex + 1 ) % players.size(), true );
        if( myIndex == 0 )
            gameActivity.setPlayerCardActive( players.size() - 1, true );
        else
            gameActivity.setPlayerCardActive( myIndex - 1, true );
        String cardClick = getClickedCard();
        if( cardClick == null ){
            cardClick = players.get( ( myIndex + 1 ) % players.size() );
            gameActivity.setRoleInfo( statements[ 13 ] );
        }
        gameActivity.setPlayersCardsActive( false );
        sendMsg( cardClick );
    }

    void waitForTingsTouch(){
        if( receive().equals( "TOUCH" ) ){
            gameActivity.setCardLabel( " -> " + statements[ 4 ] );
            gameActivity.setStatementLabel( statements[ 5 ] );
        }
    }

    void wakeUp(){
        gameActivity.setStatementLabel( statements[ 14 ] );
        gameActivity.setRoleInfo( statements[ 15 ] );
//        try{
//            wakeUpSignal.play();
//        }
//        catch( NullPointerException ignored ){}
    }

    private int vote(){
        gameActivity.setPlayersCardsActive( true );
        gameActivity.setTableCardsActive( true );
        Object voteLock = new Object();
        AtomicBoolean voteNotEnded = new AtomicBoolean( true );
        Thread votes = new Thread( () -> {
            while( true ){
                String vote = receive();
                if( vote.equals( UNIQUE_CHAR + "VOTEEND" ) ){
                    synchronized( voteLock ){
                        voteNotEnded.set( false );
                        voteNotEnded.notify();
                    }
                    break;
                }
//                Platform.runLater( () -> gameActivity.drawArrow( vote.split( MSG_SPLITTER )[ 0 ], vote.split( MSG_SPLITTER )[ 1 ] ) );
            }
        } );
        votes.start();
        String cardVoted = getClickedCard();        //TODO clicked card notify when vote ended
//        while( waitingForButton && voteNotEnded.get() );
        gameActivity.setPlayersCardsActive( false );
        gameActivity.setTableCardsActive( false );
        if( cardVoted != null ){
            if( clickedCard.substring( 0, 1 ).equals( UNIQUE_CHAR ) )
                clickedCard = UNIQUE_CHAR + "table";
            sendMsg( clickedCard );
        }
        if( voteNotEnded.get() ){
            synchronized( voteLock ){
                while( !voteNotEnded.get() ){
                    try{
                        voteNotEnded.wait();
                    } catch( InterruptedException e ){
                        break;
                    }
                }
            }
        }
        String voteResult = receive();
        if( voteResult.equals( UNIQUE_CHAR + "VOTE" ) ){      // vote again
            gameActivity.setStatementLabel( statements[ 6 ] );
//            Thread t = new Thread( () -> Platform.runLater( () -> gameActivity.clearArrows() ) );
//            t.start();
            return -1;
        }
        Vector< String > cardsNow = new Vector<>( Arrays.asList( receive().split( MSG_SPLITTER ) ) );
        Vector< String > realCardsNow = new Vector<>( Arrays.asList( receive().split( MSG_SPLITTER ) ) );
        for( int i = 0; i < players.size(); ++i ){
            if( players.get( i ).equals( nickname ) )
                gameActivity.updateMyCard( cardsNow.get( i ) );
            else
                gameActivity.reverseCard( players.get( i ), cardsNow.get( i ) );
        }
        for( int i = players.size(), j = 0; i < cardsNow.size(); ++i, ++j )
            gameActivity.reverseCard( UNIQUE_CHAR + "card" + j, cardsNow.get( i ) );
        int winner = whoWins( voteResult, realCardsNow );       // 9-tanner, 10-miasto, 11/12-wilkołaki/+minion
        if( voteResult.equals( UNIQUE_CHAR + "table" ) ){
            gameActivity.setStatementLabel( statements[ 35 ] + " - " + statements[ winner ] + "." );
        }
        else if( voteResult.equals( nickname ) )
            gameActivity.setStatementLabel( statements[ 7 ] + " - " + statements[ winner ] + "." );
        else
            gameActivity.setStatementLabel( voteResult + " " + statements[ 8 ] + " - " + statements[ winner ] + "." );
//        switch( winner ){
//            case 10: gameActivity.playMedia( "video/cityWins.mp4" ); break;
//            case 11: case 12: gameActivity.playMedia( "video/werewolvesWin.mp4" ); break;
//            case 9: gameActivity.playMedia( "video/tannerWins.mp4" ); break;
//        }
        return 0;
    }

    private int whoWins( String player, Vector< String > cardsNow ){
        if( player.equals( UNIQUE_CHAR + "table" ) ){
            if( cardsNow.contains( "Werewolf_0" ) || cardsNow.contains( "Werewolf_1" ) ||
                    cardsNow.contains( "Werewolf_2" ) || cardsNow.contains( "Mystic wolf" ) )
                return 12;
            else
                return 10;
        }
        if( cardsNow.get( players.indexOf( player ) ).equals( "Tanner" ) )
            return 9;
        if( cardsNow.get( players.indexOf( player ) ).split( "_" )[ 0 ].equals( "Werewolf" ) ||
                cardsNow.get( players.indexOf( player ) ).equals( "Mystic wolf" ) )
            return 10;
        else{
            if( cardsNow.get( players.indexOf( player ) ).equals( "Minion" ) && !minionWinsWhenHeDies )
                return 11;
            else
                return 12;
        }
    }

    public void setClickedCard( String card ){
        synchronized( clickedLock ){
            clickedCard = card;
            isClicked = true;
            isClicked.notify();
        }
    }

    private String getClickedCard(){
        synchronized( clickedLock ){
            while( !isClicked ){
                try{
                    isClicked.wait( MAX_ROLE_TIME * 1000 );
                } catch( InterruptedException e ){
                    return null;
                }
            }
            return clickedCard;
        }
    }
}
