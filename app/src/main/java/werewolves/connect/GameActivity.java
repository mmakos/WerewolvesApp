package werewolves.connect;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.IOException;
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

        drawerLayout = findViewById( R.id.mainLayout );
        findViewById( R.id.menuArrow ).setOnClickListener( v -> drawerLayout.openDrawer( GravityCompat.START ) );

        rolesLabel = findViewById( R.id.rolesLabel );
        currentRoleLabel = findViewById( R.id.currentRoleLabel );
        nicknameLabel = findViewById( R.id.nicknameLabel );
        roleDescLabel = findViewById( R.id.roleDescLabel );
        gameArea = findViewById( R.id.gameArea );
        reverseCardsSwitch = findViewById( R.id.reverseCardSwitch );
        loadingBar = findViewById( R.id.loadingBar );
        reverseCardsSwitch.setOnCheckedChangeListener( ( buttonView, isChecked ) -> hideShowCardNames( isChecked ) );
        setConsts();

        Intent intent = new Intent(this, Game.class);
        bindService( intent, serviceConnection, Context.BIND_AUTO_CREATE );
    }

    private long backPressedTime = 0;
    @SuppressLint( "ShowToast" )
    @Override
    public void onBackPressed(){
        if( backPressedTime + 1000 > System.currentTimeMillis() || game.isEnded() ){
            super.onBackPressed();
            try{
                Model.getSocket().close();
            } catch( IOException ignored ){}
            if( isBounded ){
                unbindService( serviceConnection );
                isBounded = false;
            }
            return;
        }
        else if( game.isRunning() )
            Toast.makeText( this, getString( R.string.seriouslyQuit ), Toast.LENGTH_LONG ).show();
        else if( !game.isEnded() )
            Toast.makeText( this, getString( R.string.sureToExit ), Toast.LENGTH_LONG ).show();
        backPressedTime = System.currentTimeMillis();
    }

    @Override
    protected void onDestroy(){
        try{
            Model.getSocket().close();
        } catch( IOException ignored ){}
        if( isBounded ){
            unbindService( serviceConnection );
            isBounded = false;
        }
        super.onDestroy();
    }

    @Override
    protected void onResume(){
        super.onResume();
        if( game != null )
            game.inForeground( true );
    }

    @Override
    protected void onPause(){
        super.onPause();
        if( game != null )
            game.inForeground( false );
    }

    public void abort(){
        runOnUiThread( () -> {
            Toast.makeText( this, getString( R.string.gameAborted ), Toast.LENGTH_LONG ).show();
            this.finish();
        } );
    }

    //----------- Create Game Layout --------------
    public void createTableCards(){
        int space = getPx( 10 );
        card0 = getCard( "", playersQuant );
        int x = ( sceneWidth - cardWidth ) / 2 - cardWidth - space;
        int y = ( sceneHeight - cardHeight ) / 2;
        placeCard( card0, x, y );
        card1 = getCard( "", playersQuant + 1 );
        x = x + cardWidth + space;
        placeCard( card1, x, y );
        card2 = getCard( "", playersQuant + 2 );
        x = x + cardWidth + space;
        placeCard( card2, x, y );
    }

    public void createPlayersCards(){
        playersQuant = game.players.size();
        int a = ( sceneWidth - cardWidth - getPx( 100 ) ) / 2, b = ( sceneHeight - cardHeight ) / 2, p = playersQuant;

        playersCards.setSize( p );
        knownCards.setSize( p + 3 );
//        for( String s : knownCards ) s = null;
        yourCardPos = game.players.indexOf( game.nickname );        //start drawing cards from ours
        Button button = getCard( getString( R.string.yourCard ), yourCardPos );
//        button.setId( Game.UNIQUE_CHAR + "You" );
//        button.setText( "You\n\n\n\n\n." );
        double ti = Math.toRadians( -90 );
        int x = ( int ) ( a * Math.cos( ti ) + ( sceneWidth / 2.0 ) - ( cardWidth / 2.0 ) );
        int y = ( int ) ( -1 * ( b * Math.sin( ti ) ) + ( sceneHeight / 2.0 ) - ( cardHeight / 2.0 ) );
//        button.setOpacity( 1.0 );
        button.setAlpha( 1.f );
        setCard( yourCardPos, button );
        placeCard( button, x, y );

        int pos = 0;
        for( int player = yourCardPos + 1; player < p; ++pos, ++player )
            addCard( pos, player, a, b, p );
        for( int player = 0; player < yourCardPos; ++pos, ++player )
            addCard( pos, player, a, b, p );
    }

    private void setCard( int position, Button button ){
        playersCards.set( position, button );
    }

    private void addCard( int pos, int player, int a, int b, int p ){
        String nickname = game.players.get( player );
        Button button = getCard( nickname, player );
        double ti = Math.toRadians( -90 - ( 360.0 / p ) * ( pos + 1 ) - angleDiffFunction( pos + 1, p ) );
        int x = ( int ) ( a * Math.cos( ti ) + ( sceneWidth / 2.0 ) - ( cardWidth / 2.0 ) );
        int y = ( int ) ( -1 * ( b * Math.sin( ti ) ) + ( sceneHeight / 2.0 ) - ( cardHeight / 2.0 ) );
        setCard( player, button );
        placeCard( button, x, y );
    }

    private double angleDiffFunction( int i, int p ){
        return ( -10 ) * Math.sin( Math.toRadians( ( 720.0 * i ) / p ) );
    }

    private void placeCard( Button button, int x, int y ){
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams( cardWidth, cardHeight );
        params.leftMargin = x;
        params.topMargin = y;
        runOnUiThread( () -> gameArea.addView( button, params ) );
    }

    private Button getCard( String nickname, int id ){
        Button button = new Button( this );
        button.setText( nickname );
        button.setLayoutParams( new LinearLayout.LayoutParams( cardWidth, cardHeight ) );
        button.setTextSize( buttonTextSize );
        button.setTextColor( Color.WHITE );
        button.setAllCaps( false );
        button.setPadding( 0, 0, 0, ( int ) ( cardHeight * 0.7 ) );
        runOnUiThread( () -> button.setEnabled( false ) );
        button.setAlpha( INACTIVE_OPACITY );
        button.setBackgroundResource( R.drawable.backcardsmall );
        button.setId( id );
        button.setOnClickListener( this::onClick );
        return button;
    }

    private void setConsts(){
        cardHeight = getPx( cardHeight );
        cardWidth = getPx( cardWidth );
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics( displayMetrics );
        sceneHeight = displayMetrics.heightPixels;
        sceneWidth = displayMetrics.widthPixels;
        if( sceneHeight > sceneWidth ){
            int t = sceneHeight;
            sceneHeight = sceneWidth;
            sceneWidth = t;
        }
        sceneHeight = sceneHeight - getPx( 40 );
    }

    //------------- HELP -----------------
    private int getPx( int dp ){
        return ( int ) TypedValue.applyDimension( TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics() );
    }

    private int getDrawableId( String name ){
        return getResources().getIdentifier( name, "drawable", getPackageName() );
    }

    private String getButtonName( int id ){
        int cardId = id - playersQuant;
        if( 3 > cardId && cardId >= 0 )
            return Game.UNIQUE_CHAR + "card" + cardId;
        if( id >= 0 && id < playersQuant )
            return game.players.get( id );
        return null;
    }

    public void hideLoadingBar(){
        runOnUiThread( () -> loadingBar.setVisibility( View.INVISIBLE ) );
    }

    //------------- Set GUI fields -------------
    public void setCardLabel( String str ){
        runOnUiThread( () -> rolesLabel.setText( String.format( "%s%s", rolesLabel.getText(), str ) ) );
    }

    public void setCardLabelBegin( String str ){
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
                runOnUiThread( () -> button.setEnabled( active ) );
                if( reverseCardsSwitch.isChecked() || knownCards.get( playersCards.indexOf( button ) ) == null )
                    runOnUiThread( () -> button.setAlpha( active ? 1.f : INACTIVE_OPACITY ) );
            }
            ++i;
        }
    }

    public void setPlayerCardActive( int playerIndex, boolean active ){
        runOnUiThread( () -> playersCards.get( playerIndex ).setEnabled( active ) );
        if( reverseCardsSwitch.isChecked() || knownCards.get( playerIndex ) == null )
            runOnUiThread( () -> playersCards.get( playerIndex ).setAlpha( active ? 1.f : INACTIVE_OPACITY ) );
    }

    public void setTableCardsActive( boolean active ){
        runOnUiThread( () -> {
            card0.setEnabled( active );
            card1.setEnabled( active );
            card2.setEnabled( active );
            boolean rc = reverseCardsSwitch.isChecked();
            if( rc || knownCards.get( playersQuant ) == null ) card0.setAlpha( active ? 1.f : INACTIVE_OPACITY );
            if( rc || knownCards.get( playersQuant + 1 ) == null ) card1.setAlpha( active ? 1.f : INACTIVE_OPACITY );
            if( rc ||knownCards.get( playersQuant + 2 ) == null ) card2.setAlpha( active ? 1.f : INACTIVE_OPACITY );
        } );
    }

    public void setTableCardActive( String card, boolean active ){
        Button btn;
        int idx;
        switch( card ){
            case ( char )2 + "card0": btn = card0; idx = playersQuant; break;
            case ( char )2 + "card1": btn = card1; idx = playersQuant + 1; break;
            case ( char )2 + "card2": btn = card2; idx = playersQuant + 2; break;
            default: return;
        }
        runOnUiThread( () -> btn.setEnabled( active ) );
        if( reverseCardsSwitch.isChecked() || knownCards.get( idx ) == null )
            runOnUiThread( () -> btn.setAlpha( active ? 1.f : INACTIVE_OPACITY ) );
    }

    public void hideCenterCard( String card ){
        Button btn;
        int idx;
        switch( card ){
            case ( char )2 + "card0": btn = card0; idx = playersQuant; break;
            case ( char )2 + "card1": btn = card1; idx = playersQuant + 1; break;
            case ( char )2 + "card2": btn = card2; idx = playersQuant + 2; break;
            default: return;
        }
        runOnUiThread( () -> {
                    btn.setBackgroundResource( R.drawable.backcardsmall );
                    btn.setAlpha( INACTIVE_OPACITY );
                } );
        knownCards.set( idx, null );
    }

    public void updateMyCard( String card ){
        reverseCard( game.nickname, card );
        knownCards.set( game.players.indexOf( game.nickname ), null );
        game.displayedCard = card;
    }

    public void reverseCard( String player, String card ){
        Button btn;
        int idx;
        switch( player ){
            case ( char )2 + "card0": btn = card0; idx = playersQuant; break;
            case ( char )2 + "card1": btn = card1; idx = playersQuant + 1; break;
            case ( char )2 + "card2": btn = card2; idx = playersQuant + 2; break;
            default: idx = game.players.indexOf( player ); btn = playersCards.get( idx );
        }
        knownCards.set( idx, card );
        int TIME = 1000 / 2;
        ObjectAnimator animation = ObjectAnimator.ofFloat( btn, "rotationY", 0.0f, 90f ).setDuration( TIME );
        animation.setInterpolator( new AccelerateInterpolator() );
        ObjectAnimator anim2 = ObjectAnimator.ofFloat( btn, "rotationY", 270f, 360f ).setDuration( TIME );
        anim2.setInterpolator( new DecelerateInterpolator() );
        anim2.setStartDelay( TIME );
        runOnUiThread( () -> {
            if( !reverseCardsSwitch.isEnabled() || !player.equals( game.nickname ) )
                reverseCardsSwitch.setEnabled( true );
            animation.start();
            anim2.start();
            new Handler().postDelayed( () -> {
                btn.setBackgroundResource( getDrawableId( "frontcardbig" + card.split( " " )[ 0 ].toLowerCase() ) );
                btn.setAlpha( 1.0f );
            }, TIME );
        } );
    }

    public void dereverse( Button card ){
        int TIME = 1000 / 2;
        ObjectAnimator rot1 = ObjectAnimator.ofFloat( card, "rotationY", 0.0f, -90f ).setDuration( TIME );
        ObjectAnimator rot2 = ObjectAnimator.ofFloat( card, "rotationY", 90f, 0f ).setDuration( TIME );
        rot1.setInterpolator( new AccelerateInterpolator() );
        rot2.setInterpolator( new DecelerateInterpolator() );
        rot2.setStartDelay( TIME );
        runOnUiThread( () -> {
            rot1.start();
            rot2.start();
            new Handler().postDelayed( () -> {
                card.setBackgroundResource( R.drawable.backcardsmall );
                if( !card.isEnabled() )
                    card.setAlpha( INACTIVE_OPACITY );
            }, TIME );
        } );
    }

    public void swapCardsAnimation( String player1, String player2 ){
        int TIME = 1200;
        Button c1, c2;
        int idx1, idx2;
        switch( player1 ){
            case ( char )2 + "card0": c1 = card0; idx1 = playersQuant; break;
            case ( char )2 + "card1": c1 = card1; idx1 = playersQuant + 1; break;
            case ( char )2 + "card2": c1 = card2; idx1 = playersQuant + 2; break;
            default: idx1 = game.players.indexOf( player1 ); c1 = playersCards.get( idx1 );
        }
        switch( player2 ){
            case ( char )2 + "card0": c2 = card0; idx2 = playersQuant; break;
            case ( char )2 + "card1": c2 = card1; idx2 = playersQuant + 1; break;
            case ( char )2 + "card2": c2 = card2; idx2 = playersQuant + 2; break;
            default: idx2 = game.players.indexOf( player2 ); c2 = playersCards.get( idx2 );
        }
        String tempC = knownCards.get( idx1 );
        knownCards.set( idx1, knownCards.get( idx2 ) );
        knownCards.set( idx2, tempC );
        Button card1 = c1;
        Button card2 = c2;
        CharSequence card1Text = card1.getText(), card2Text = card2.getText();
        float deltaX = card2.getX() - card1.getX();
        float deltaY = card2.getY() - card1.getY();
        // Translation
        AnimatorSet translate = new AnimatorSet();
        translate.setDuration( TIME ).playTogether(
                ObjectAnimator.ofFloat( card1, "translationX", deltaX ),
                ObjectAnimator.ofFloat( card1, "translationY", deltaY ),
                ObjectAnimator.ofFloat( card2, "translationX", -deltaX ),
                ObjectAnimator.ofFloat( card2, "translationY", -deltaY ) );
        // Scale down card1
        AnimatorSet scale1 = new AnimatorSet();
        scale1.setDuration( TIME / 2 ).playTogether(
                ObjectAnimator.ofFloat( card1, "scaleX", 0.8f ),
                ObjectAnimator.ofFloat( card1, "scaleY", 0.8f ),
                ObjectAnimator.ofFloat( card2, "scaleX", 1.2f ),
                ObjectAnimator.ofFloat( card2, "scaleY", 1.2f ) );
        AnimatorSet scale2 = new AnimatorSet();
        scale2.setDuration( TIME / 2 ).playTogether(
                ObjectAnimator.ofFloat( card1, "scaleX", 1.0f ),
                ObjectAnimator.ofFloat( card1, "scaleY", 1.0f ),
                ObjectAnimator.ofFloat( card2, "scaleX", 1.0f ),
                ObjectAnimator.ofFloat( card2, "scaleY", 1.0f ) );
        scale2.setStartDelay( TIME / 2 );
        translate.addListener( new Animator.AnimatorListener(){
            @Override public void onAnimationStart( Animator animation ){}
            @Override
            public void onAnimationEnd( Animator animation ){
                // change backgrounds
                Drawable tempD = card1.getBackground();
                card1.setBackground( card2.getBackground() );
                card2.setBackground( tempD );
                // change alpha
                float tempA = card1.getAlpha();
                card1.setAlpha( card2.getAlpha() );
                card2.setAlpha( tempA );
                // restore positions
                card1.setTranslationX( .0f );
                card1.setTranslationY( .0f );
                card2.setTranslationX( .0f );
                card2.setTranslationY( .0f );
                // restore names
                card1.setText( card1Text );
                card2.setText( card2Text );
            }
            @Override public void onAnimationCancel( Animator animation ){}
            @Override public void onAnimationRepeat( Animator animation ){}
        } );
        runOnUiThread( () -> {
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ){
                card1.setElevation( 2f );
                card2.setElevation( 1f );
            }
            card1.setText( "" );
            card2.setText( "" );
            translate.start();
            scale1.start();
            scale2.start();
        } );
    }

    public void hideShowCardNames( boolean isChecked ){
        runOnUiThread( () -> {
            if( isChecked ){
                if( knownCards.get( playersQuant ) != null ) dereverse( card0 );
                if( knownCards.get( playersQuant + 1 ) != null ) dereverse( card1 );
                if( knownCards.get( playersQuant + 2 ) != null ) dereverse( card2 );
                for( int i = 0; i < playersQuant; ++i ){
                    if( knownCards.get( i ) != null )
                        dereverse( playersCards.get( i ) );
                }
            } else{
                for( int i = 0; i < playersQuant; ++i ){
                    if( knownCards.get( i ) != null )
                        reverseCard( game.players.get( i ), knownCards.get( i ) );
                }
                for( int i = playersQuant, j = 0; i < knownCards.size(); ++i, ++j ){
                    if( knownCards.get( i ) != null )
                        reverseCard( Game.UNIQUE_CHAR + "card" + j, knownCards.get( i ) );
                }
            }
        } );
    }

    public void clearArrows(){
        runOnUiThread( () -> {
            for( Arrow a : arrows )
                gameArea.removeView( a );
            arrows.clear();
        } );
    }

    public void drawArrow( String from, String to ){
        Button fromButton = playersCards.get( game.players.indexOf( from ) );
        double x1, y1, x2, y2;
        x1 = fromButton.getX() + ( double )cardWidth / 2;
        y1 = fromButton.getY() + ( double )cardHeight / 2;
        if( to.equals( Game.UNIQUE_CHAR + "table" ) ){
            x2 = ( double ) sceneWidth / 2;
            y2 = ( double ) sceneHeight / 2;
        }
        else{
            Button toButton = playersCards.get( game.players.indexOf( to ) );
            int a = cardHeight / 2;
            x2 = toButton.getX() + ( double )cardWidth / 2;
            y2 = toButton.getY() + ( double )cardHeight / 2;
            double dist = Math.hypot( x2 - x1, y2 - y1 );
            x2 = x2 - ( a * ( x2 - x1 ) / dist );
            y2 = y2 - ( a * ( y2 - y1 ) / dist );
        }
        double finalX = x2;
        double finalY = y2;
        runOnUiThread( () -> {
            Arrow a = new Arrow( this );
            arrows.add( a );
            gameArea.addView( a );
            if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP )
                a.setElevation( 10.f );
            else
                a.bringToFront();
            a.draw( ( int )x1, ( int ) y1, ( int )finalX, ( int ) finalY, 2000 );
        } );
    }

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

    private RelativeLayout gameArea;
    private DrawerLayout drawerLayout;
    @SuppressLint( "UseSwitchCompatOrMaterialCode" )
    private Switch reverseCardsSwitch;
    private ProgressBar loadingBar;
    private Button card0, card1, card2;
    private final Vector< Button > playersCards = new Vector<>();
    private int playersQuant;
    private int yourCardPos;
    private final Vector< Arrow > arrows = new Vector<>();
    public Vector< String > knownCards = new Vector<>();
    private TextView rolesLabel;
    private TextView currentRoleLabel;
    private TextView nicknameLabel;
    private TextView roleDescLabel;
    private int sceneWidth, sceneHeight;
    private int cardHeight = ( int ) ( 100 * 0.8 );
    private int cardWidth = ( int ) ( 72 * 0.8 );
    private static final int buttonTextSize = 10;
    private final static float INACTIVE_OPACITY = 0.5f;
    private Game game;
    private boolean isBounded = false;
    private final ServiceConnection serviceConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected( ComponentName name, IBinder service ){
            Game.MyBinder binder = ( Game.MyBinder ) service;
            game = binder.getService();
            game.initGameActivity( GameActivity.this );
            isBounded = true;
        }

        @Override
        public void onServiceDisconnected( ComponentName name ){
            isBounded = false;
        }
    };
}
