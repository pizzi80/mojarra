/*
 * Copyright (c) 1997, 2020 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.faces.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.naming.InitialContext;

import jakarta.faces.FacesException;

/**
 * <p>
 * This utility class is to provide both encryption and decryption <code>Ciphers</code> to
 * <code>ResponseStateManager</code> implementations wishing to provide encryption support.
 * </p>
 *
 * <p>
 * The algorithm used to encrypt byte array is AES with CBC.
 * </p>
 *
 * <p>
 * Original author Inderjeet Singh, J2EE Blue Prints Team. Modified to suit Faces needs.
 * </p>
 */
public final class ByteArrayGuardAESCTR {

    // Log instance for this class
    private static final Logger LOGGER = FacesLogger.RENDERKIT.getLogger();

    private static final int KEY_LENGTH = 128;
    private static final int IV_LENGTH = 16;

    private static final String KEY_ALGORITHM = "AES";
    private static final String CIPHER_CODE = "AES/CTR/NoPadding";

    private SecretKey secretKey;

    // ------------------------------------------------------------ Constructors

    public ByteArrayGuardAESCTR() {
        try {
            setupSecretKey();
        }
        catch (Exception e) {
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.log(Level.SEVERE, "Unexpected exception initializing encryption." + "  No encryption will be performed.", e);
            }
        }
    }

    // ---------------------------------------------------------- Public Methods

    /**
     * This method: Encrypts bytes using a cipher. Generates MAC for initialization vector of the cipher Generates MAC for
     * encrypted data Returns a byte array consisting of the following concatenated together: |MAC for encrypted Data | MAC
     * for Init Vector | Encrypted Data |
     *
     * @param value The value to be encrypted.
     * @return the encrypted value.
     */
    public String encrypt(String value) {
        byte[] bytes = value.getBytes(UTF_8);
        try {
            byte[] iv = randomIV();
            IvParameterSpec params = new IvParameterSpec(iv);
            Cipher encryptCipher = Cipher.getInstance(CIPHER_CODE);

            encryptCipher.init(Cipher.ENCRYPT_MODE, secretKey, params);

            // encrypt the plaintext
            byte[] encrypted = encryptCipher.doFinal(bytes);
            byte[] data = concatBytes(iv, encrypted);

            // Base64 encode the encrypted bytes
            final String encoded = Base64.getEncoder().encodeToString(data);
            return encoded;
        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException
                | BadPaddingException e) {
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.log(Level.SEVERE, "Unexpected exception initializing encryption." + "  No encryption will be performed.", e);
            }
            return null;
        }
        catch (Exception e) {
            throw new FacesException(e);
        }
    }

    public String decrypt(String value) throws InvalidKeyException {
        // decode from Base64
        byte[] decoded = Base64.getDecoder().decode(value);

        if (decoded.length < IV_LENGTH) {
            throw new InvalidKeyException("Invalid characters in decrypted value");
        }

        try {

            // copy first 16 bytes from decode value to iv byte array
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(decoded, 0, iv, 0, iv.length);
            IvParameterSpec param = new IvParameterSpec(iv);

            Cipher decryptCipher = Cipher.getInstance(CIPHER_CODE);
            decryptCipher.init(Cipher.DECRYPT_MODE, secretKey, param);

            // copy the remaining bytes
            byte[] encrypted = new byte[decoded.length - IV_LENGTH];
            System.arraycopy(decoded, IV_LENGTH, encrypted, 0, encrypted.length);

            byte[] decrypted = decryptCipher.doFinal(encrypted);

            for (byte cur : decrypted) {
                // Values < 0 cause the conversion to text to fail.
                if (cur < 0) {
                    throw new InvalidKeyException("Invalid characters in decrypted value");
                }
            }
            return new String(decrypted, UTF_8);
        }
        catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidAlgorithmParameterException | IllegalBlockSizeException
                | BadPaddingException e) {
            throw new InvalidKeyException(e);
        }
    }

    // --------------------------------------------------------- Private Methods

    private static boolean loaded = false;
    private static final SecretKeySpec userDefinedKey = initUserDefinedKey();   // can be null

    // this method could be synchronized with
    // an inner block + double check on
    // volatile boolean loaded,
    // but it's not strictly needed
    public static SecretKeySpec initUserDefinedKey() {
        SecretKeySpec key = null;
        if (!loaded) {
            key = loadUserDefinedSecretKey();
            loaded = true;
        }
        return key;
    }

    private static SecretKeySpec loadUserDefinedSecretKey() {
        try {
            final InitialContext context = new InitialContext();
            final String encodedSecretKey = (String) context.lookup("java:comp/env/faces/FlashSecretKey");
            if (encodedSecretKey != null) {
                final byte[] decoded = Base64.getDecoder().decode(encodedSecretKey);
                if (decoded.length < IV_LENGTH) {
                    if (LOGGER.isLoggable(Level.WARNING)) {
                        LOGGER.log(Level.WARNING, "FlashSecretKey must be at least 16 bytes long. Using a random key");
                    }
                }
                final SecretKeySpec userDefinedKey = new SecretKeySpec(decoded, KEY_ALGORITHM);
                return userDefinedKey;
            }
        } catch (Throwable exception) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "Unable to find the encoded key at java:comp/env/faces/FlashSecretKey", exception);
            }
        }
        return null;
    }

    private void setupSecretKey() {
        // Let's see if an encoded key was given to the application, if so use it and skip the code to generate it.
        if ( userDefinedKey != null ) {
            secretKey = userDefinedKey;
        }
        else {
            try {
                KeyGenerator generator = KeyGenerator.getInstance(KEY_ALGORITHM);
                generator.init(KEY_LENGTH); // 256 if you're using the Unlimited Policy Files
                secretKey = generator.generateKey();
            } catch (Exception e) {
                throw new FacesException(e);
            }
        }
    }

    // Utils --------------------------------------------------------------------------------

    /**
     * The random number generator used by this class to create random values.
     * In a holder class to defer initialization until needed.
     */
    private static class DeferredSecureRandom {
        static final SecureRandom random = initSecureRandom();
        static SecureRandom initSecureRandom() { return new SecureRandom(); }
    }

    /**
     * @return an array of 16 random bytes
     */
    private static byte[] randomIV() {
        final byte[] bytes = new byte[IV_LENGTH];
        DeferredSecureRandom.random.nextBytes(bytes);
        return bytes;
    }

    /**
     * This method concatenates two byte arrays
     *
     * @return a byte array that is the concatenation of array1 and array2
     * @param array1 first byte array to be concatenated
     * @param array2 second byte array to be concatenated
     */
    private static byte[] concatBytes(final byte[] array1, final byte[] array2) {
        final byte[] output = new byte[array1.length + array2.length];
        System.arraycopy(array1, 0, output, 0, array1.length);
        System.arraycopy(array2, 0, output, array1.length, array2.length);
        return output;
    }

}