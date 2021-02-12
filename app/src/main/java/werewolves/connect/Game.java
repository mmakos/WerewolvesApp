package werewolves.connect;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Game{
    private final Object clickedLock = new Object();        // to synchronize clicked card moment
    private Boolean isClicked = false;
    private String clickedCard;
    public static final transient String COM_SPLITTER = String.valueOf( ( char )28 );
    public final static transient String MSG_SPLITTER = String.valueOf( ( char )29 );
    public final static transient String UNIQUE_CHAR = String.valueOf( ( char )2 );
    public final static transient int MAX_ROLE_TIME = 30;
    private static final transient boolean minionWinsWhenHeDies = true;
    public Vector< String > players;
    private String card;
    public String displayedCard;        // When you are copycat or paranormal then its value is different than card line above
    public String nickname;
    public String[] statements = { "wakes up",
            "wakes up - YOUR TURN",
            "Vote",
            "You became",
            "you've been touched",
            "Thing touches you",
            "Vote again, decision must be unequivocal",
            "You have been killed",
            "has been killed",
            "tanner wins",
            "city wins",
            "werewolves win",
            "werewolves and minion win",
            "Time's up. Card will be randomly selected.",
            "City wakes up!",
            "Now you need to connect with other players via outer application, such as Zoom, to establish who is who. When you will be ready, admin will press 'start vote' button and you'll be able to make your vote on person, you wish to be dead.",
            "Choose one card from the middle. From this moment you will become the card you chose.",
            "On the top left corner you can see which card you were, and which card you are now.",
            "You are the only werewolf. Select one card from the middle you wish to see.",
            "Other werewolves are",
            "There is no werewolves among the players.",
            "Werewolves are",
            "Choose one card to reverse",
            "You can give a card from the middle to one of players. You will see this card. Choose one card from the middle and then select player whom you want to give this card to.",
            "Now choose player.",
            "You can swap players's cards. Choose two cards to swap.",
            "Choose second player.",
            "Choose other player's card. If it is tanner or werewolf, you became that card (but it's not a swap). If it's not, you have to choose second card. If it's still not werewolf or tanner you play with city.",
            "You can see, what card you are on the end.",
            "Choose player, whose card you want to robb.",
            "Choose player on your left or right, you want to touch. He feels your touch and can confirm it during day.",
            "Choose two cards from the center you want to see.",
            "Now you will see who is a Seer.",
            "Seer is in the center or it's not in the game at all.",
            "You have to choose second card. If it's still not werewolf or tanner you play with city.",
            "Nobody has been killed",
            "Hide card names",
            "Show card names",
            "You are" };
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    GameActivity gameActivity;
    BlockingQueue< String > msgQueue = new LinkedBlockingQueue<>();

    Game( GameActivity gameActivity ){
        this.gameActivity = gameActivity;
        this.card = Model.getCard();
        this.nickname = Model.getNickname();
        this.players = Model.getPlayers();
        this.socket = Model.getSocket();
        try{
            input = new BufferedReader( new InputStreamReader( socket.getInputStream(), Charset.forName( "UTF-8" ) ) );
            output = new PrintWriter( new OutputStreamWriter( socket.getOutputStream(), Charset.forName( "UTF-8" ) ), true );
        } catch( IOException ignored ){}
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

    public void runGame(){
        gameActivity.setCardLabel( card.split( "_" )[ 0 ] );
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
                String msgCard = msg.charAt( 0 ) + msg.substring( 1 ).toLowerCase();
                gameActivity.setStatementLabel( msgCard + " " + statements[ 0 ] );
                if( msg.equals( card.split( "_" )[ 0 ].toUpperCase() ) || ( msg.equals( "WEREWOLF" ) && card.equals( "Mystic wolf" ) ) ){
                    gameActivity.setStatementLabel( msgCard + " " + statements[ 1 ] );
                    try {
                        proceedCard( msgCard );
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                if( msg.equals( "THING" ) )
                    waitForTingsTouch();
            }
            if( receive().equals( UNIQUE_CHAR + "VOTE" ) ){
                gameActivity.setStatementLabel( statements[ 2 ] );
                while( vote() != 0 );       // Not busy waiting, just repeat voting until it returns 0.
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
        gameActivity.setTableCardActive( cardClick, false );
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
                        voteLock.notify();
                    }
                    synchronized( clickedLock ){
                        clickedLock.notify();
                    }
                    break;
                }
//                Platform.runLater( () -> gameActivity.drawArrow( vote.split( MSG_SPLITTER )[ 0 ], vote.split( MSG_SPLITTER )[ 1 ] ) );
            }
        } );
        votes.start();
        Log.i( "MyMsg", "Want to get vote." );
        String cardVoted = getClickedCard( true );
        Log.i( "MyMsg", "Got vote." );
        gameActivity.setPlayersCardsActive( false );
        gameActivity.setTableCardsActive( false );
        if( cardVoted != null && voteNotEnded.get() ){
            if( clickedCard.substring( 0, 1 ).equals( UNIQUE_CHAR ) )
                clickedCard = UNIQUE_CHAR + "table";
            sendMsg( clickedCard );
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
            clickedLock.notify();
        }
    }

    private String getClickedCard( boolean vote ){
        synchronized( clickedLock ){
            while( !isClicked ){
                try{
                    clickedLock.wait( MAX_ROLE_TIME * 1000 );
                    if( !vote ){
                        clickedCard = null;
                        break;
                    }
                } catch( InterruptedException e ){
                    return null;
                }
            }
            isClicked = false;
            return clickedCard;
        }
    }

    private String getClickedCard(){
        return getClickedCard( false );
    }
}
