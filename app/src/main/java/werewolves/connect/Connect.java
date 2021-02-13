package werewolves.connect;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Parcelable;

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
import java.util.Vector;

import static werewolves.connect.Game.COM_SPLITTER;
import static werewolves.connect.Game.MSG_SPLITTER;

public class Connect implements AsyncResponse{
    private int port = 23000;
    private String ip = "185.20.175.81";
    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private final ConnectActivity connectActivity;
    private final String nickname;
    private final String gameID;
    private Vector< String > players = new Vector<>();
    private String card;

    Connect( String nickname, String gameID, ConnectActivity cA ){
        this.connectActivity = cA;
        this.nickname = nickname;
        this.gameID = gameID;
        Connect.ConnectNet connectNet = new Connect.ConnectNet();
        connectNet.delegate = this;
        connectNet.execute();
    }

    @Override
    public void onConnected( boolean connected ){
        connectActivity.connected( connected );
        if( !connected )
            return;
        // Things after connected to the server
        Connect.StartGame startGame = new Connect.StartGame();
        startGame.delegate = this;
        startGame.execute();
    }

    @Override
    public void onGameStarted(){
        Intent intent = new Intent( connectActivity, GameActivity.class );
        Model.setCard( card );
        Model.setNickname( nickname );
        Model.setPlayers( players );
        Model.setSocket( socket );
        Model.setInput( input );
        Model.setOutput( output );
        connectActivity.startActivity( intent );
    }

    private class ConnectNet extends AsyncTask< Void, Void, String >{
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
                socket.setKeepAlive( true );
                socket.connect( new InetSocketAddress( ip, port ), 5000 );
                input = new BufferedReader( new InputStreamReader( socket.getInputStream(), Charset.forName( "UTF-8" ) ) );
                output = new PrintWriter( new OutputStreamWriter( socket.getOutputStream(), Charset.forName( "UTF-8" ) ), true );
                sendToServer( gameID );
                if( !receive().equals( "GOOD" ) )
                    return connectActivity.getString( R.string.noSuchGame );
                sendMsg( nickname );
                String nickInfo = receive();
                if( nickInfo.equals( "WRONGNICK" ) )
                    return connectActivity.getString( R.string.nicknameTaken );
                if( !nickInfo.equals( "OK" ) )
                    return connectActivity.getString( R.string.somethingWrong );
            } catch( IOException e ){
                return connectActivity.getString( R.string.cannotConnect );
            }
            return connectActivity.getString( R.string.gameWillStart );
        }

        @Override
        protected void onPostExecute( String result ) {
            super.onPostExecute( result );
            connectActivity.info( result );
            boolean connected = result.equals( connectActivity.getString( R.string.noSuchGame ) );
            if( !connected ){
                try{
                    socket.close();
                } catch( IOException ignored ){}
            }
            delegate.onConnected( connected );
        }
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

    private class StartGame extends AsyncTask< Void, Void, Void >{
        public AsyncResponse delegate = null;
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
            delegate.onGameStarted();
        }
    }

    private void getPlayers(){
        String msg = receive();
        String[] playersTab = msg.split( MSG_SPLITTER, 0 );
        players.addAll( Arrays.asList( playersTab ) );
    }

    private void getCard(){
        card = receive();
    }
}
