package werewolves.connect;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.util.Objects;
import java.util.Vector;

public class GameActivity extends AppCompatActivity implements View.OnClickListener{

    @Override
    protected void onCreate( @Nullable Bundle savedInstanceState ){
        super.onCreate( savedInstanceState );
        getWindow().setFlags( WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN );
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2 )
            setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE );
        else
            setRequestedOrientation( ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE );
        Objects.requireNonNull( getSupportActionBar() ).hide();
        setContentView( R.layout.game_activity );
        Intent intent = getIntent();
        game = ( Game ) intent.getSerializableExtra( "gameObject" );
        game.setGameActivity( this );

        final DrawerLayout drawerLayout = findViewById( R.id.mainLayout );
        findViewById( R.id.menuArrow ).setOnClickListener( v -> drawerLayout.openDrawer( GravityCompat.START ) );

        rolesLabel = findViewById( R.id.rolesLabel );
        currentRoleLabel = findViewById( R.id.currentRoleLabel );
        nicknameLabel = findViewById( R.id.nicknameLabel );
        roleDescLabel = findViewById( R.id.roleDescLabel );
        gameArea = findViewById( R.id.gameArea );
        setConsts();
        createTableCards();
        createPlayersCards();
    }

    //----------- Create Game Layout --------------
    public void createTableCards(){
        card0 = getCard( "" );
        int x = ( sceneWidth - cardWidth ) / 2 - cardWidth;
        int y = ( sceneHeight - cardHeight ) / 2;
        placeCard( card0, x, y );
        card1 = getCard( "" );
        x = x + cardWidth;
        placeCard( card1, x, y );
        card2 = getCard( "" );
        x = x + cardWidth;
        placeCard( card2, x, y );
    }

    public void createPlayersCards(){
        int a = ( sceneWidth - cardWidth - getPx( 100 ) ) / 2, b = ( sceneHeight - cardHeight ) / 2, p = 11;

        playersCards.setSize( p );
        knownCards.setSize( p + 3 );
//        for( String s : knownCards ) s = null;
//        int ourPos = game.players.indexOf( game.nickname );        //start drawing cards from ours
        yourCardPos = 3;
        Button button = getCard( "Your card" );
//        button.setId( Game.UNIQUE_CHAR + "You" );
//        button.setText( "You\n\n\n\n\n." );
        double ti = Math.toRadians( -90 );
        int x = ( int ) ( a * Math.cos( ti ) + ( sceneWidth / 2.0 ) - ( cardWidth / 2.0 ) );
        int y = ( int ) ( -1 * ( b * Math.sin( ti ) ) + ( sceneHeight / 2.0 ) - ( cardHeight / 2.0 ) );
//        button.setOpacity( 1.0 );
        button.setAlpha( INACTIVE_OPACITY );        // TODO to 1.f
        setCard( yourCardPos, button, Game.UNIQUE_CHAR + "You" );
        placeCard( button, x, y );

        int pos = 0;
        for( int player = yourCardPos + 1; player < p; ++pos, ++player )
            addCard( pos, player, a, b, p );
        for( int player = 0; player < yourCardPos; ++pos, ++player )
            addCard( pos, player, a, b, p );
    }

    private void setCard( int position, Button button, String name ){
        playersCards.set( position, button );
        cardsAttr.set( position, new ButtonAttr( button.getId(), name ) );
    }

    private void addCard( int pos, int player, int a, int b, int p ){
        String nickname = "player" + player;
//        String nickname = game.players.get( player );     TODO
        Button button = getCard( nickname );
        double ti = Math.toRadians( -90 - ( 360.0 / p ) * ( pos + 1 ) - angleDiffFunction( pos + 1, p ) );
        int x = ( int ) ( a * Math.cos( ti ) + ( sceneWidth / 2.0 ) - ( cardWidth / 2.0 ) );
        int y = ( int ) ( -1 * ( b * Math.sin( ti ) ) + ( sceneHeight / 2.0 ) - ( cardHeight / 2.0 ) );
        playersCards.set( player, button );
        setCard( player, button, nickname );
        placeCard( button, x, y );
    }

    private double angleDiffFunction( int i, int p ){
        return  ( -10 ) * Math.sin( Math.toRadians( ( 720.0 * i ) / p ) );
    }

    private void placeCard( Button button, int x, int y ){
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams( cardWidth, cardHeight );
        params.leftMargin = x;
        params.topMargin = y;
        gameArea.addView( button, params );
    }

    private Button getCard( String nickname ){
        Button button = new Button( this );
        button.setText( nickname );
        button.setLayoutParams( new LinearLayout.LayoutParams( cardWidth, cardHeight ) );
        button.setTextSize( buttonTextSize );
        button.setTextColor( Color.WHITE );
        button.setAllCaps( false );
        button.setPadding( 0, 0, 0, ( int ) ( cardHeight * 0.7 ) );
        button.setEnabled( false );
        button.setBackgroundResource( R.drawable.backcardsmall );
        button.setOnClickListener( this );
        return button;
    }

    private void setConsts(){
        cardHeight = getPx( cardHeight );
        cardWidth = getPx( cardWidth );
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics( displayMetrics );
        sceneHeight = displayMetrics.heightPixels;
        sceneWidth = displayMetrics.widthPixels - getPx( 40 );
        if( sceneHeight > sceneWidth ){
            int t = sceneHeight;
            sceneHeight = sceneWidth;
            sceneWidth = t;
        }

    }

    //------------- HELP -----------------
    private int getPx( int dp ){
        return ( int ) TypedValue.applyDimension( TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics() );
    }

    private int getDrawableId( String name ){
        return getResources().getIdentifier( name, "drawable", getPackageName() );
    }

    private String getButtonName( int id ){
        for( ButtonAttr ba : cardsAttr ){
            if( ba.id == id )
                return ba.name;
        }
        return null;
    }

    //------------- Set GUI fields -------------
    public void setCardLabel( String str ){
        runOnUiThread( () -> rolesLabel.setText( str ) );
    }

    public void setStatementLabel( String str ){
        runOnUiThread( () -> currentRoleLabel.setText( str ) );
    }

    public void setRoleInfo( String str ){
        runOnUiThread( () -> roleDescLabel.setText( str ) );
    }

    public void setNicknameLabel( String str ){
        runOnUiThread( () -> nicknameLabel.setText( str ) );
    }

    public void setPlayersCardsActive( boolean active ){
        int i = 0;
        for( Button button: playersCards ){
            if( i != yourCardPos ){
                button.setEnabled( active );
                if( reverseCardsSwitch.isChecked() || knownCards.get( playersCards.indexOf( button ) ) == null )
                    button.setAlpha( active ? 1.f : INACTIVE_OPACITY );
            }
            ++i;
        }
    }

    public void setPlayerCardActive( int playerIndex, boolean active ){
        playersCards.get( playerIndex ).setEnabled( active );
        if( reverseCardsSwitch.isChecked() || knownCards.get( playerIndex ) == null )
            playersCards.get( playerIndex ).setAlpha( active ? 1.f : INACTIVE_OPACITY );
    }

    public void setTableCardsActive( boolean active ){
        card0.setEnabled( active );
        card1.setEnabled( active );
        card2.setEnabled( active );
        if( reverseCardsSwitch.isChecked() || knownCards.get( knownCards.size() - 3 ) == null ) card0.setAlpha( active ? 1.f : INACTIVE_OPACITY );
        if( reverseCardsSwitch.isChecked() || knownCards.get( knownCards.size() - 2 ) == null ) card1.setAlpha( active ? 1.f : INACTIVE_OPACITY );
        if( reverseCardsSwitch.isChecked() || knownCards.get( knownCards.size() - 1 ) == null ) card2.setAlpha( active ? 1.f : INACTIVE_OPACITY );
    }

    public void hideCenterCard( String card ){
        Button btn;
        int idx;
        switch( card ){
            case ( char )2 + "card0": btn = card0; idx = knownCards.size() - 3; break;
            case ( char )2 + "card1": btn = card1; idx = knownCards.size() - 2; break;
            case ( char )2 + "card2": btn = card2; idx = knownCards.size() - 1; break;
            default: return;
        }
        btn.setBackgroundResource( R.drawable.backcardsmall );
        btn.setAlpha( INACTIVE_OPACITY );
        knownCards.set( idx, null );
    }

    public void updateMyCard( String card ){
        Button btn = playersCards.get( game.players.indexOf( game.nickname ) );
        game.displayedCard = card;
        runOnUiThread( () -> btn.setBackgroundResource( getDrawableId( "frontcardbig" + card.split( " " )[ 0 ]  ) ) );
    }

    public void reverseCard( String player, String card ){
        Button btn;
        int idx;
        switch( player ){
            case ( char )2 + "card0": btn = card0; idx = knownCards.size() - 3; break;
            case ( char )2 + "card1": btn = card1; idx = knownCards.size() - 2; break;
            case ( char )2 + "card2": btn = card2; idx = knownCards.size() - 1; break;
            default: idx = game.players.indexOf( player ); btn = playersCards.get( idx );
        }
        knownCards.set( idx, card );
        runOnUiThread( () -> {
            if( !reverseCardsSwitch.isEnabled() )
                reverseCardsSwitch.setEnabled( true );
            runOnUiThread( () -> btn.setBackgroundResource( getDrawableId( "frontcardbig" + card.split( " " )[ 0 ]  ) ) );
            btn.setAlpha( 1.0f );
        } );
    }

    public void hideShowCardNames(){
        if( reverseCardsSwitch.isChecked() ){
            card0.setBackgroundResource( R.drawable.backcardsmall );
            card1.setBackgroundResource( R.drawable.backcardsmall );
            card2.setBackgroundResource( R.drawable.backcardsmall );
            if( !card0.isEnabled() ) card0.setAlpha( INACTIVE_OPACITY );
            if( !card1.isEnabled() ) card1.setAlpha( INACTIVE_OPACITY );
            if( !card2.isEnabled() ) card2.setAlpha( INACTIVE_OPACITY );
            for( int i = 0; i < playersCards.size(); ++i ){
                if( knownCards.get( i ) != null ){
                    playersCards.get( i ).setBackgroundResource( R.drawable.backcardsmall );
                    if( !playersCards.get( i ).isEnabled() ) playersCards.get( i ).setAlpha( INACTIVE_OPACITY );
                }
            }
        } else{
            for( int i = 0; i < playersCards.size(); ++i ){
                if( knownCards.get( i ) != null )
                    reverseCard( game.players.get( i ), knownCards.get( i ) );
            }
            for( int i = playersCards.size(), j = 0; i < knownCards.size(); ++i, ++j ){
                if( knownCards.get( i ) != null )
                    reverseCard( Game.UNIQUE_CHAR + "card" + j, knownCards.get( i ) );
            }
        }
    }

    @Override
    public void onClick( View v ){
        int btnId = v.getId();
        String card;
        if( btnId == card0.getId() )
            card = Game.UNIQUE_CHAR + "card0";
        else if( btnId == card1.getId() )
            card = Game.UNIQUE_CHAR + "card1";
        else if( btnId == card2.getId() )
            card = Game.UNIQUE_CHAR + "card2";
        else
            card = getButtonName( btnId );
        if( card != null )
            this.game.setClickedCard( card );
    }

//    @FXML private AnchorPane gamePane;
//    @FXML private MediaView video;
    private RelativeLayout gameArea;
    @SuppressLint( "UseSwitchCompatOrMaterialCode" )
    private Switch reverseCardsSwitch;
    private Button card0, card1, card2;
    private Vector< Button > playersCards = new Vector<>();
    private Vector< ButtonAttr > cardsAttr = new Vector<>();
    private int yourCardPos;
//    private Vector< Line > lines = new Vector<>();
    public Vector< String > knownCards = new Vector<>();
    private TextView rolesLabel;
    private TextView currentRoleLabel;
    private TextView nicknameLabel;
    private TextView roleDescLabel;
    private static int sceneWidth, sceneHeight;
    private static int cardHeight = ( int ) ( 100 * 0.8 );
    private static int cardWidth = ( int ) ( 72 * 0.8 );
    private static int buttonTextSize = 10;
    private final static float INACTIVE_OPACITY = 0.5f;
    private Game game;

    private class ButtonAttr{
        public int id;
        public String name;
        ButtonAttr( int id, String name ){
            this.id = id;
            this.name = name;
        }
    }
}
