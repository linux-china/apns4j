/*
 *  Copyright (c) 2009, Jonathan Garay Mendoza
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
 *      * Redistributions of source code must retain the above copyright
 *        notice, this list of conditions and the following disclaimer.
 *      * Redistributions in binary form must reproduce the above copyright
 *        notice, this list of conditions and the following disclaimer in the
 *        documentation and/or other materials provided with the distribution.
 *      * Neither the name of the <organization> nor the
 *        names of its contributors may be used to endorse or promote products
 *        derived from this software without specific prior written permission.
 *
 *  THIS SOFTWARE IS PROVIDED BY Jonathan Garay ''AS IS'' AND ANY
 *  EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL <copyright holder> BE LIABLE FOR ANY
 *  DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 *  ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package net.devmask.apns;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.cert.CertificateException;

/**
 * User: Jonathan Garay <netmask@webtelmex.net.mx>
 * Created: Apr 9, 2009 2:57:37 PM
 */
public class APNSConnectionHandler implements APNSConnectionMonitor {
    private Log log = LogFactory.getLog(APNSConnectionHandler.class);
    private SSLSocketFactory sslSocketFactory;
    private SSLContext sslContext;
    private KeyStore keyStore;
    private KeyManagerFactory keyManagerFactory;


    private volatile APNSConnection apnsConnection;

    public static String apnsHost = "gateway.push.apple.com"; //gateway.sandbox.push.apple.com
    public static int apnsPort = 2195;

    private boolean persistent;

    /**
     * Inits the connectionHandler with a given configuration
     *
     * @param config connection config
     */
    public APNSConnectionHandler(APNSConnectionConfig config) throws NoSuchAlgorithmException,
            KeyManagementException, IOException, CertificateException, UnrecoverableKeyException, KeyStoreException, Exception {
        this(config.getP12File(), config.getP12FileToken().toCharArray());
        persistent = config.isPersistent();
    }

    /**
     * Basic constructor of ASPN Connection
     * loads the key char file and passparse
     * sets the number of concurrent connections to APNS service
     *
     * @param keyFile   key file
     * @param passParse pass parse
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     * @throws KeyManagementException
     */
    public APNSConnectionHandler(File keyFile, char[] passParse) throws KeyStoreException,
            NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException, KeyManagementException, Exception {
        this(keyFile, passParse, false);
    }

    /**
     * Basic constructor of ASPN Connection
     * loads the key char file and passparse
     * sets the number of concurrent connections to APNS service
     *
     * @param keyInputStream key input steam
     * @param passParse      pass parse
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     * @throws KeyManagementException
     */
    public APNSConnectionHandler(InputStream keyInputStream, char[] passParse) throws KeyStoreException,
            NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException, KeyManagementException, Exception {
        this(keyInputStream, passParse, false);
    }

    /**
     * Basic constructor of ASPN Connection
     * loads the key char file and passparse
     * sets the number of concurrent connections to APNS service
     *
     * @param keyFile   key file
     * @param passParse pass parse
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     * @throws KeyManagementException
     */
    public APNSConnectionHandler(File keyFile, char[] passParse, boolean persistent) throws KeyStoreException,
            NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException, KeyManagementException, Exception {
        this(new FileInputStream(keyFile), passParse, persistent);
    }

    /**
     * Basic constructor of ASPN Connection
     * loads the key char file and passparse
     * sets the number of concurrent connections to APNS service
     *
     * @param keyInputStream key input stream
     * @param passParse      pass parse
     * @throws KeyStoreException
     * @throws NoSuchAlgorithmException
     * @throws IOException
     * @throws CertificateException
     * @throws UnrecoverableKeyException
     * @throws KeyManagementException
     */
    public APNSConnectionHandler(InputStream keyInputStream, char[] passParse, boolean persistent) throws KeyStoreException,
            NoSuchAlgorithmException, IOException, CertificateException, UnrecoverableKeyException, KeyManagementException, Exception {
        if (passParse.length <= 0) {
            throw new Exception("Exception passParse cant be null OR blank");
        }
        keyStore = KeyStore.getInstance("PKCS12");
        keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
        sslContext = SSLContext.getInstance("TLS");
        keyStore.load(keyInputStream, passParse);
        keyManagerFactory.init(keyStore, passParse);
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
        sslSocketFactory = sslContext.getSocketFactory();
        this.persistent = persistent;
        initSocketConnection();
    }

    /**
     * Init the connection
     */
    public void initSocketConnection() {

        try {
            SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(apnsHost, apnsPort);
            socket.startHandshake();
            log.info("Authentication Succes");
            socket.setKeepAlive(true);

            apnsConnection = new APNSConnection(socket, persistent);

        } catch (Exception ex) {
            log.error(ex.getLocalizedMessage(), ex);
        }


    }


    /**
     * Sends a message
     *
     * @param APNSMessage
     */
    public void sendMessage(APNSMessage APNSMessage) {
        apnsConnection.addMessage(APNSMessage);
    }

    /**
     * Sennd a bulk of messages on a single connection
     *
     * @param APNSMessages
     */
    public void sendMessages(APNSMessage[] APNSMessages) {
        apnsConnection.addBulkMessages(APNSMessages);
    }


    public void onAPNSStop(SSLSocket socket) {
        log.warn("APNS: Un spected shutdown");
        if (persistent) initSocketConnection();

    }
}
