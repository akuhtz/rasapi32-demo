package jnarasapi.demo;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.ptr.IntByReference;

import jnarasapi.jna.Rasapi32;
import jnarasapi.jna.Rasapi32Util;
import jnarasapi.jna.Rasapi32Util.Ras32Exception;
import jnarasapi.jna.WinRas;

public class RasApi32Demo {

    private static final Logger LOGGER = LoggerFactory.getLogger(RasApi32Demo.class);

    private final ScheduledExecutorService scheduledExecutorService;

    private static final String CONN_NAME = "VPN-DEV-TEST";

    public static void main(String[] args) {
        LOGGER.info("Start the RasApi32Demo.");

        new RasApi32Demo();

    }

    public RasApi32Demo() {

        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
        scheduledExecutorService.scheduleWithFixedDelay( () -> {
            try {
                LOGGER.info("Check the RAS connections.");
                checkRasConnections();
            }
            catch (Exception ex) {
                LOGGER.warn("Check the RAS connections failed.", ex);
            }
        }, 5, 5, TimeUnit.SECONDS);

    }


    public void checkRasConnections() {

        dumpRasConnectionInfo();



        // use Rasapi32Util.getRasConnection to get the connection handle
        HANDLE handle = Rasapi32Util.getRasConnection(CONN_NAME);
        LOGGER.info("Current connection handle: {}", handle);
    }

    private void dumpRasConnectionInfo() {

        // get the ras connection info based on information from:
        // https://stackoverflow.com/questions/54879439/rasenumconnections-function-in-jna-is-returning-incomplete-data-what-i-am-doing

        IntByReference lpcb = new IntByReference(0);
        IntByReference lpcConnections = new IntByReference();
        int err = Rasapi32.INSTANCE.RasEnumConnections(null, lpcb, lpcConnections);
        if (err != WinError.ERROR_SUCCESS && err != WinRas.ERROR_BUFFER_TOO_SMALL) {
            throw new Ras32Exception(err);
        }

        LOGGER.info("Fetched all active RAS connections. Current connection count: {}, cb: {}", lpcConnections.getValue(), lpcb.getValue());

        if (lpcConnections.getValue() == 0) {
            return;
        }

        final Memory mem = new Memory(lpcb.getValue());
        try {

            WinRas.RASCONN conn = new WinRas.RASCONN(mem);
            conn.dwSize = lpcb.getValue() / lpcConnections.getValue();

            LOGGER.info("RASCONN Size: {}", conn.dwSize);

            WinRas.RASCONN[] connArray = (WinRas.RASCONN[]) conn.toArray(lpcConnections.getValue());

            for (int idx = 0; idx < lpcConnections.getValue(); idx++) {
                connArray[idx].dwSize = lpcb.getValue() / lpcConnections.getValue();
            }

            LOGGER.info("lpcb: " + lpcb.getValue() + " lpcConnections: " + lpcConnections.getValue() + " RASCONN Size: " + conn.dwSize);
            int error = Rasapi32.INSTANCE.RasEnumConnections(connArray, lpcb, lpcConnections);

            if(error != WinError.ERROR_SUCCESS) {
                LOGGER.warn("Enumerate connections failed, error: {}", Rasapi32Util.getRasErrorString(error));
                throw new Ras32Exception(err);
            }

            // find connection
            for(int i = 0; i < lpcConnections.getValue(); i++) {

                LOGGER.info("Current connection: '{}'", new String(connArray[i].szEntryName));

                LOGGER.info("Current connection szEntryName[0]: '{}', szEntryName[1]: '{}'", (int) connArray[i].szEntryName[0], (int) connArray[i].szEntryName[1]);

                LOGGER.info("Current connection dwSize: {}", connArray[i].dwSize);
                LOGGER.info("Current connection hrasconn: {}", connArray[i].hrasconn);

                LOGGER.info("Current szDeviceType: '{}'", new String(connArray[i].szDeviceType));
                LOGGER.info("Current szDeviceName: '{}'", new String(connArray[i].szDeviceName));
                LOGGER.info("Current szPhonebook: '{}'", new String(connArray[i].szPhonebook));
            }
        }
        catch (Exception ex) {
            LOGGER.warn("Fetch connections data failed.", ex);
        }
        finally {
            LOGGER.info("Free mem.");
            mem.close();
        }
    }

}
