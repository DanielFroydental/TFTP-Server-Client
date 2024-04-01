package bgu.spl.net.srv;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ConnectionsImpl<T> implements Connections<T> {

    private int nextConnectionId = 0;
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> Id_handler = new ConcurrentHashMap<>();
    private Object lock =new Object();


    @Override
    public boolean connect(int connectionId, ConnectionHandler<T> handler) {
        if (Id_handler.put(connectionId, handler) == null) {
            return true;
        }
        return false;
    }

    @Override
    public boolean send(int connectionId, T msg) { // remember to check if logged in
        ConnectionHandler<T> curr = Id_handler.get(connectionId);
        synchronized(lock){
            if (curr != null){
                curr.send(msg); // implement send
                return true;
            }
            return false;
        }
        
    }

    @Override
    public void disconnect(int connectionId) {
        Id_handler.remove(connectionId);
    }

    public int getNextID() {
        return ++nextConnectionId;
    }

}

