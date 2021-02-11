package werewolves.connect;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Layout;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.util.Objects;
import java.util.Vector;

public class GameActivity extends AppCompatActivity{
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
        card0id = Game.UNIQUE_CHAR + "card0";
        card1id = Game.UNIQUE_CHAR + "card1";
        card2id = Game.UNIQUE_CHAR + "card2";
    }

    public void createPlayersCards(){
        int a = ( sceneWidth - cardWidth - getPx( 100 ) ) / 2, b = ( sceneHeight - cardHeight ) / 2, p = 11;

        playersCards.setSize( p );
        knownCards.setSize( p + 3 );
//        knownCards.forEach( ( knownCard ) -> knownCard = "" );
//        int ourPos = game.players.indexOf( game.nickname );        //start drawing cards from ours
        yourCardPos = 3;
        Button button = getCard( "Your card" );
//        button.setId( Game.UNIQUE_CHAR + "You" );
//        button.setText( "You\n\n\n\n\n." );
        double ti = Math.toRadians( -90 );
        int x = ( int ) ( a * Math.cos( ti ) + ( sceneWidth / 2.0 ) - ( cardWidth / 2.0 ) );
        int y = ( int ) ( -1 * ( b * Math.sin( ti ) ) + ( sceneHeight / 2.0 ) - ( cardHeight / 2.0 ) );
//        button.setOpacity( 1.0 );
        playersCards.set( yourCardPos, button );
        placeCard( button, x, y );

        int pos = 0;
        for( int player = yourCardPos + 1; player < p; ++pos, ++player )
            addCard( pos, player, a, b, p );
        for( int player = 0; player < yourCardPos; ++pos, ++player )
            addCard( pos, player, a, b, p );
    }

    private void addCard( int pos, int player, int a, int b, int p ){
        // ToggleButton toggle = getPlayerCard( game.players.get( player ) );
        Button button = getCard( "player" + player );
        double ti = Math.toRadians( -90 - ( 360.0 / p ) * ( pos + 1 ) - angleDiffFunction( pos + 1, p ) );
        int x = ( int ) ( a * Math.cos( ti ) + ( sceneWidth / 2.0 ) - ( cardWidth / 2.0 ) );
        int y = ( int ) ( -1 * ( b * Math.sin( ti ) ) + ( sceneHeight / 2.0 ) - ( cardHeight / 2.0 ) );
        playersCards.set( player, button );
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

        return button;
    }

    private void setConsts(){
        cardHeight = getPx( cardHeight );
        cardWidth = getPx( cardWidth );
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics( displayMetrics );
        sceneWidth = displayMetrics.heightPixels;
        sceneHeight = displayMetrics.widthPixels - getPx( 40 );
    }

    //------------- HELP -----------------
    private int getPx( int dp ){
        return ( int ) TypedValue.applyDimension( TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics() );
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
                    button.getBackground().setAlpha( active ? 255 : 127 );
            }
            ++i;
        }
    }

//    @FXML private AnchorPane gamePane;
//    @FXML private MediaView video;
    private RelativeLayout gameArea;
    @SuppressLint( "UseSwitchCompatOrMaterialCode" )
    private Switch reverseCardsSwitch;
    private Button card0, card1, card2;
    private String card0id, card1id, card2id;
    private Vector< Button > playersCards = new Vector<>();
    private int yourCardPos;
//    private Vector< Line > lines = new Vector<>();
    public Vector< String > knownCards = new Vector<>();
    private TextView rolesLabel;
    private TextView currentRoleLabel;
    private TextView nicknameLabel;
    private TextView roleDescLabel;
//    @FXML private Button reverseCardButton;
//    @FXML public Button quitButton;
    private static int sceneWidth, sceneHeight;
    private static int cardHeight = ( int ) ( 100 * 0.8 );
    private static int cardWidth = ( int ) ( 72 * 0.8 );
    private static int buttonTextSize = 10;
}
