package werewolves.connect;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Game extends Service{
    private final Object clickedLock = new Object();        // to synchronize clicked card moment
    private Boolean isClicked = false;
    private boolean isRunning = false;
    private boolean isEnded = false;
    private String clickedCard;
    public static final transient String COM_SPLITTER = String.valueOf( ( char )28 );
    public final static transient String MSG_SPLITTER = String.valueOf( ( char )29 );
    public final static transient String UNIQUE_CHAR = String.valueOf( ( char )2 );
    private final static int KEEP_ALIVE_TIME = 300; // in seconds
    public final static transient int MAX_ROLE_TIME = 30;
    private static final transient boolean minionWinsWhenHeDies = true;
    public Vector< String > players = new Vector<>();
    private String card;
    public String displayedCard;        // When you are copycat or paranormal then its value is different than card line above
    public String nickname;
    private BufferedReader input;
    private PrintWriter output;
    GameActivity gameActivity;
    Thread keepAlive;
    BlockingQueue< String > msgQueue = new LinkedBlockingQueue<>();

    public void initGameActivity( GameActivity gameActivity ){
        this.gameActivity = gameActivity;
        this.nickname = Model.getNickname();
        this.input = Model.getInput();
        this.output = Model.getOutput();
        gameActivity.setStatementLabel(getString( R.string.waitForPlayers ) );
        gameActivity.setNicknameLabel( nickname );

        new GameNet().execute();

        keepAlive = new Thread( () -> {
            while( true ){
                try{
                    Thread.sleep( KEEP_ALIVE_TIME * 1000 );
                    output.println( UNIQUE_CHAR + "ALIVE" );
                } catch( InterruptedException e ){
                    break;
                }
            }
        } );
        keepAlive.start();
        gameLogic();
    }

    public boolean isRunning(){
        return isRunning;
    }
    public boolean isEnded(){ return isEnded; }

    private final IBinder binder = new MyBinder();
    @Nullable
    @Override
    public IBinder onBind( Intent intent ){
        return binder;
    }

    public class MyBinder extends Binder{
        Game getService(){
            return Game.this;
        }
    }


    private class GameNet extends AsyncTask< Void, Void, Void >{
        @Override
        protected Void doInBackground( Void... voids ){
            try{
                getPlayers();
                gameActivity.createPlayersCards();
                isRunning = true;       // now you shouldn't exit the game
                gameActivity.setStatementLabel(getString( R.string.waitForCards ) );
                getCard();
                gameActivity.swapCardsAnimation( players.get( 0 ), players.get( 1 ) );
                gameActivity.createTableCards();
                gameActivity.setCardLabelBegin( getString( R.string.yourCardLabel ) + " " + card.split( "_" )[ 0 ] );
                gameActivity.updateMyCard( card );
                gameActivity.hideLoadingBar();
            } catch( IOException e ){
                return null;
            }
            while( true ){
                try{
                    String msg = input.readLine();
                    if( msg == null ){
                        abort();
                        return null;
                    }
                    else if( msg.equals( UNIQUE_CHAR + "ENDGAME" ) )
                        return null;
                    else if( msg.equals( UNIQUE_CHAR + "ALIVE" ) ){
                        continue;
                    }
                    msgQueue.put( msg );
                } catch( IOException | InterruptedException ignored ){}
            }
        }
    }

    private void getPlayers() throws IOException{
        String msg = input.readLine();
        String[] playersTab = msg.split( MSG_SPLITTER, 0 );
        players.addAll( Arrays.asList( playersTab ) );
    }

    private void getCard() throws IOException{
        card = input.readLine();
    }

    private void abort(){
        keepAlive.interrupt();
        gameActivity.abort();
    }

    private String getRandomPlayerCard(){
        int rand = new Random().nextInt( players.size() - 1 );
        if( rand == players.indexOf( nickname ) ) rand = players.size() - 1;
        return players.get( rand );
    }

    public void sendMsg( String str ){
        output.println( "ADM" + COM_SPLITTER + str );
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
                String msg = read();
                if( msg.equals( "WakeUp" ) ){
                    wakeUp();
                    break;
                }
                String msgCard = msg.charAt( 0 ) + msg.substring( 1 ).toLowerCase();
                gameActivity.setStatementLabel( msgCard + " " + getString( R.string.wakesUp ) );
                if( msg.equals( card.split( "_" )[ 0 ].toUpperCase() ) || ( msg.equals( "WEREWOLF" ) && card.equals( "Mystic wolf" ) ) ){
                    gameActivity.setStatementLabel( msgCard + " " + getString( R.string.yourTurn ) );
                    proceedCard( msgCard );
                }
                if( msg.equals( "THING" ) )
                    waitForTingsTouch();
            }
            if( read().equals( UNIQUE_CHAR + "VOTE" ) ){
                gameActivity.setStatementLabel( getString( R.string.vote ) );
                while( vote() != 0 );       // Not busy waiting, just repeat voting until it returns 0.
                isRunning = false;
                isEnded = true;
            }
        } );
        gameLogic.start();
    }

    private void proceedCard( String card ){
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
        gameActivity.setRoleInfo( getString( R.string.copycat1 ) );
        gameActivity.setTableCardsActive( true );

        // Waiting for clicked card, but with time limit of 30 seconds
        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
            gameActivity.setRoleInfo( getString( R.string.timeUp ) + "\n" +
                    getString( R.string.copycat2 ) );
        }
        else
            gameActivity.setRoleInfo( getString( R.string.copycat2 ) );
        gameActivity.setTableCardsActive( false );
        sendMsg( cardClick );
        card = read();
        gameActivity.setStatementLabel( getString( R.string.youBecame ) + " " + card.split( "_" )[ 0 ] );
        gameActivity.reverseCard( cardClick, card );
        gameActivity.setCardLabel( " -> " + card.split( "_" )[ 0 ] );
    }

    void makeWerewolf(){
        StringBuilder str = new StringBuilder();
        String[] werewolves = read().split( MSG_SPLITTER );
        for( String werewolf: werewolves ){
            if( !werewolf.equals( nickname ) ){
                gameActivity.reverseCard( werewolf, "Werewolf_0" );
                str.append( " " ).append( werewolf );
            }
        }
        if( str.toString().isEmpty() ){
            gameActivity.setRoleInfo( getString( R.string.werewolf1 ) );
            gameActivity.setTableCardsActive( true );
            String cardClick = getClickedCard();
            if( cardClick == null ){
                int rand = new Random().nextInt( 3 );
                cardClick = UNIQUE_CHAR + "card" + rand;
                gameActivity.setRoleInfo( getString( R.string.timeUp ) );
            }
            gameActivity.setTableCardsActive( false );
            sendMsg( cardClick );
            String chosenCard = read();
            gameActivity.reverseCard( cardClick, chosenCard );
        }
        else
            gameActivity.setRoleInfo( getString( R.string.werewolf2 ) + str.toString() + "." );
    }

    void makeMinion(){
        StringBuilder str = new StringBuilder();
        String[] werewolves = read().split( MSG_SPLITTER, 0 );
        if( !werewolves[ 0 ].equals( "" ) ){
            for( String werewolf : werewolves ){
                gameActivity.reverseCard( werewolf, "Werewolf_0" );
                str.append( " " ).append( werewolf );
            }
        }
        if( str.toString().isEmpty() )
            gameActivity.setRoleInfo( getString( R.string.minion1 ) );
        else
            gameActivity.setRoleInfo( getString( R.string.minion2 ) + str.toString() + "." );
    }

    void makeMysticWolf(){
        gameActivity.setRoleInfo( getString( R.string.mystic ) );
        gameActivity.setTableCardsActive( true );

        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
            gameActivity.setRoleInfo( getString( R.string.timeUp ) );
        }
        gameActivity.setTableCardsActive( false );
        sendMsg( cardClick );
        String chosenCard = read();
        gameActivity.reverseCard( cardClick, chosenCard );
    }
    void makeApprenticeSeer(){
        gameActivity.setRoleInfo( getString( R.string.apprentice ) );
        gameActivity.setTableCardsActive( true );

        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
            gameActivity.setRoleInfo( getString( R.string.timeUp ) );
        }
        gameActivity.setTableCardsActive( false );
        sendMsg( cardClick );
        String chosenCard = read();
        gameActivity.reverseCard( cardClick, chosenCard );
    }
    void makeWitch(){
        gameActivity.setRoleInfo( getString( R.string.witch1 ) );
        gameActivity.setTableCardsActive( true );

        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
            gameActivity.setRoleInfo( getString( R.string.timeUp ) + "\n" + getString( R.string.witch2 ) );
        }
        gameActivity.setTableCardsActive( false );
        sendMsg( cardClick );
        String chosenCard = read();
        gameActivity.reverseCard( cardClick, chosenCard );
        String firstClickedCard = cardClick;

        gameActivity.setPlayersCardsActive( true );
        cardClick = getClickedCard();
        if( cardClick == null ){
            cardClick = getRandomPlayerCard();
            gameActivity.setRoleInfo( getString( R.string.timeUp ) );
        }
        gameActivity.setPlayersCardsActive( false );
        gameActivity.swapCardsAnimation( cardClick, firstClickedCard );
//        gameActivity.hideCenterCard( firstClickedCard );
//        gameActivity.reverseCard( cardClick, chosenCard );
        sendMsg( cardClick );
    }

    void makeTroublemaker(){
        gameActivity.setRoleInfo( getString( R.string.troublemaker1 ) );
        gameActivity.setPlayersCardsActive( true );

        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            cardClick = getRandomPlayerCard();
            gameActivity.setRoleInfo( getString( R.string.timeUp ) + "\n" + getString( R.string.troublemaker2 ) );
        }
        String cards = cardClick + MSG_SPLITTER;
        gameActivity.setPlayerCardActive( players.indexOf( cardClick ), false );
        String cardClick2 = getClickedCard();
        if( cardClick2 == null ){
            cardClick2 = getRandomPlayerCard();
            gameActivity.setRoleInfo( getString( R.string.timeUp ) );
        }
        cards += cardClick2;
        gameActivity.setPlayersCardsActive( false );
        gameActivity.swapCardsAnimation( cardClick, cardClick2 );
        sendMsg( cards );
    }

    void makeBeholder(){
        gameActivity.setRoleInfo( getString( R.string.beholder1 ) );
        String msg = read();
        if( msg.equals( "NoSeer" ) ) gameActivity.setRoleInfo( getString( R.string.beholder2 ) );
        else gameActivity.reverseCard(msg,"Seer");
    }

    void makeSeer(){
        gameActivity.setRoleInfo( getString( R.string.seer1 ) );
        gameActivity.setTableCardsActive( true );

        String cardClick = getClickedCard();
        // If time is up, card will be selected randomly
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
            gameActivity.setRoleInfo( getString( R.string.timeUp ) + " " + getString( R.string.seer2 ) );
        }
        gameActivity.setTableCardActive( cardClick, false );
        String cards = cardClick + MSG_SPLITTER;
        cardClick = getClickedCard();
        if( cardClick == null ){
            int rand = new Random().nextInt( 3 );
            cardClick = UNIQUE_CHAR + "card" + rand;
            gameActivity.setRoleInfo( getString( R.string.timeUp ) );
        }
        cards += cardClick;
        sendMsg( cards );
        String[] cardsInCenter = read().split( MSG_SPLITTER );
        String[] clickedCards = cards.split( MSG_SPLITTER );
        gameActivity.setTableCardsActive( false );
        gameActivity.reverseCard( clickedCards[ 0 ], cardsInCenter[ 0 ] );
        gameActivity.reverseCard( clickedCards[ 1 ], cardsInCenter[ 1 ] );
    }
    void makeInsomniac(){
        gameActivity.setRoleInfo( getString( R.string.insomniac ) );
        String insomniacNow = read();
        gameActivity.setCardLabel( " -> " + insomniacNow );
        gameActivity.updateMyCard( insomniacNow );
    }

    void makeParanormal(){
        gameActivity.setRoleInfo( getString( R.string.paranormal1 ) );
        gameActivity.setPlayersCardsActive( true );
        for( int i = 0; i < 2; ++i ){
            String cardClick = getClickedCard();
            if( cardClick == null ){
                cardClick = getRandomPlayerCard();
                gameActivity.setRoleInfo( getString( R.string.timeUp ) + "\n" + getString( R.string.paranormal2 ) );
            }
            else
                gameActivity.setRoleInfo( getString( R.string.paranormal2 ) );
            sendMsg( cardClick );
            String msg = read();
            gameActivity.reverseCard( cardClick, msg );
            msg = msg.split( "_" )[ 0 ];
            if( msg.equals( "Tanner" ) || msg.equals( "Werewolf" ) || msg.equals( "Mystic wolf" ) ){
                gameActivity.setCardLabel( " -> " + msg );
                gameActivity.setStatementLabel( getString( R.string.youBecame ) + " " + msg );
                break;
            }
            if( i == 2 )
                gameActivity.setRoleInfo( getString( R.string.paranormal3 ) );
        }
        gameActivity.setPlayersCardsActive( false );
    }

    void makeRobber(){
        gameActivity.setRoleInfo( getString( R.string.robber ) );
        gameActivity.setPlayersCardsActive( true );
        String cardClick = getClickedCard();
        if( cardClick == null ){
            cardClick = getRandomPlayerCard();
            gameActivity.setRoleInfo( getString( R.string.timeUp ) );
        }
        gameActivity.setPlayersCardsActive( false );
        sendMsg( cardClick );
        String msg = read();
        String msg2 = msg.split( "_" )[ 0 ];
        gameActivity.setCardLabel( " -> " + msg2 );
        gameActivity.setStatementLabel( getString( R.string.youBecame ) + " " + msg2 );
        gameActivity.reverseCard( cardClick, msg );
        gameActivity.swapCardsAnimation( cardClick, nickname );
//        gameActivity.updateMyCard( msg );
    }

    void makeThing(){
        gameActivity.setRoleInfo( getString( R.string.thing ) );
        int myIndex = players.indexOf( nickname );
        gameActivity.setPlayerCardActive( ( myIndex + 1 ) % players.size(), true );
        if( myIndex == 0 )
            gameActivity.setPlayerCardActive( players.size() - 1, true );
        else
            gameActivity.setPlayerCardActive( myIndex - 1, true );
        String cardClick = getClickedCard();
        if( cardClick == null ){
            cardClick = players.get( ( myIndex + 1 ) % players.size() );
            gameActivity.setRoleInfo( getString( R.string.timeUp ) );
        }
        gameActivity.setPlayersCardsActive( false );
        sendMsg( cardClick );
    }

    void waitForTingsTouch(){
        if( read().equals( "TOUCH" ) ){
            gameActivity.setCardLabel( " -> " + getString( R.string.touched ) );
            gameActivity.setStatementLabel( getString( R.string.thingTouch ) );
        }
    }

    void wakeUp(){
        gameActivity.setStatementLabel( getString( R.string.cityWakeUp ) );
        gameActivity.setRoleInfo( getString( R.string.connectViaZoom ) );
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
                String vote = read();
                if( vote.equals( UNIQUE_CHAR + "VOTEEND" ) ){
                    synchronized( voteLock ){
                        voteNotEnded.set( false );
                        voteLock.notify();
                    }
                    synchronized( clickedLock ){
                        clickedLock.notify();
                    }
                    break;
                }
                gameActivity.drawArrow( vote.split( MSG_SPLITTER )[ 0 ], vote.split( MSG_SPLITTER )[ 1 ] );
            }
        } );
        votes.start();
        String cardVoted = getClickedCard( true );
        gameActivity.setPlayersCardsActive( false );
        gameActivity.setTableCardsActive( false );
        if( cardVoted != null && voteNotEnded.get() ){
            if( cardVoted.substring( 0, 1 ).equals( UNIQUE_CHAR ) )
                cardVoted = UNIQUE_CHAR + "table";
            sendMsg( cardVoted );
        }
        synchronized( voteLock ){
            while( voteNotEnded.get() ){
                try{
                    voteLock.wait();
                } catch( InterruptedException e ){
                    break;
                }
            }
        }
        String voteResult = read();
        if( voteResult.equals( UNIQUE_CHAR + "VOTE" ) ){      // vote again
            gameActivity.setStatementLabel( getString( R.string.voteAgain ) );
            gameActivity.clearArrows();
            return -1;
        }
        Vector< String > cardsNow = new Vector<>( Arrays.asList( read().split( MSG_SPLITTER ) ) );
        Vector< String > realCardsNow = new Vector<>( Arrays.asList( read().split( MSG_SPLITTER ) ) );
        for( int i = 0; i < players.size(); ++i ){
            if( players.get( i ).equals( nickname ) )
                gameActivity.updateMyCard( cardsNow.get( i ) );
            else
                gameActivity.reverseCard( players.get( i ), cardsNow.get( i ) );
        }
        for( int i = players.size(), j = 0; i < cardsNow.size(); ++i, ++j )
            gameActivity.reverseCard( UNIQUE_CHAR + "card" + j, cardsNow.get( i ) );
        int winner = whoWins( voteResult, realCardsNow );       // 9-tanner, 10-miasto, 11/12-wilkołaki/+minion
        String winnerStr = "";
        switch( winner ){
            case 9: getString( R.string.tannerWins ); break;
            case 10: getString( R.string.cityWins ); break;
            case 11: getString( R.string.werewolvesWin ); break;
            case 12: getString( R.string.minionWin ); break;
        }
        if( voteResult.equals( UNIQUE_CHAR + "table" ) )
            gameActivity.setStatementLabel( getString( R.string.nobodyKilled ) + " - " + winnerStr + "." );
        else if( voteResult.equals( nickname ) )
            gameActivity.setStatementLabel( getString( R.string.youAreKilled ) + " - " + winnerStr + "." );
        else
            gameActivity.setStatementLabel( voteResult + " " + getString( R.string.hasBeenKilled ) + " - " + winnerStr + "." );
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

    public void setClickedCard( String c ){
        synchronized( clickedLock ){
            clickedCard = c;
            isClicked = true;
            clickedLock.notify();
        }
    }

    private String getClickedCard( boolean vote ){
        synchronized( clickedLock ){
            while( !isClicked ){
                try{
                    clickedLock.wait( MAX_ROLE_TIME * 1000 );
                    if( !vote )
                        break;
                } catch( InterruptedException e ){
                    return null;
                }
            }
            isClicked = false;
            String c = clickedCard;
            clickedCard = null;
            return c;
        }
    }

    private String getClickedCard(){
        return getClickedCard( false );
    }
}
