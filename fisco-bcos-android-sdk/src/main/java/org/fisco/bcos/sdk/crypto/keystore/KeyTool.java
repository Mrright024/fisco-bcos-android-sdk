/**
 * Copyright 2014-2020 [fisco-dev]
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fisco.bcos.sdk.crypto.keystore;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Collections;
import org.fisco.bcos.sdk.crypto.exceptions.LoadKeyStoreException;
import org.fisco.bcos.sdk.utils.Numeric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.ECDomainParameters;
import org.spongycastle.crypto.params.ECPrivateKeyParameters;
import org.spongycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.spongycastle.jcajce.provider.asymmetric.ec.BCECPublicKey;
import org.spongycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.spongycastle.jce.ECNamedCurveTable;
import org.spongycastle.jce.provider.BouncyCastleProvider;
import org.spongycastle.jce.spec.ECNamedCurveParameterSpec;
import org.spongycastle.jce.spec.ECNamedCurveSpec;
import org.spongycastle.jce.spec.ECPrivateKeySpec;
import org.spongycastle.util.io.pem.PemObject;
import org.spongycastle.util.io.pem.PemWriter;

public abstract class KeyTool {
    protected static Logger logger = LoggerFactory.getLogger(KeyTool.class);

    protected final String keyStoreFile;
    protected final String password;
    protected KeyStore keyStore;
    private String hexedPublicKey = "";

    /**
     * constructor for the P12: with password
     *
     * @param keyStoreFile the path of the keystore file
     * @param password password to read the keystore file
     */
    public KeyTool(final String keyStoreFile, final String password) {
        this.keyStoreFile = keyStoreFile;
        this.password = password;
        initSecurity();
        load();
    }

    /**
     * constructor for PEM without password
     *
     * @param keyStoreFile the path of the keystore file
     */
    public KeyTool(final String keyStoreFile) {
        this(keyStoreFile, null);
    }

    /**
     * constructor for the P12: with password and key file input stream
     *
     * @param keyStoreFileInputStream the input stream of the keystore file
     * @param password password to read the keystore file
     */
    public KeyTool(InputStream keyStoreFileInputStream, final String password) {
        this.keyStoreFile = null;
        this.password = password;
        initSecurity();
        this.load(keyStoreFileInputStream);
    }

    /**
     * constructor for PEM with key file input stream
     *
     * @param keyStoreFileInputStream the input stream of the keystore file
     */
    public KeyTool(InputStream keyStoreFileInputStream) {
        this(keyStoreFileInputStream, null);
    }

    private static void initSecurity() {
        Security.setProperty("crypto.policy", "unlimited");
        Security.addProvider(new BouncyCastleProvider());
    }

    public static String getHexedPublicKey(PublicKey publicKey) {
        byte[] publicKeyBytes = ((BCECPublicKey) publicKey).getQ().getEncoded(false);
        BigInteger publicKeyValue =
                new BigInteger(1, Arrays.copyOfRange(publicKeyBytes, 1, publicKeyBytes.length));
        return ("04" + Numeric.toHexStringNoPrefixZeroPadded(publicKeyValue, 128));
    }

    public static String getHexedPrivateKey(PrivateKey privateKey) {
        return Numeric.toHexStringNoPrefixZeroPadded(((BCECPrivateKey) privateKey).getD(), 64);
    }

    /**
     * convert hexed string into PrivateKey type storePublicKeyWithPem
     *
     * @param hexedPrivateKey the hexed privateKey
     * @param curveName the curve name
     * @return the converted privateKey
     * @throws LoadKeyStoreException convert exception, return exception information
     */
    public static PrivateKey convertHexedStringToPrivateKey(
            String hexedPrivateKey, String curveName) throws LoadKeyStoreException {
        BigInteger privateKeyValue = new BigInteger(hexedPrivateKey, 16);
        return convertHexedStringToPrivateKey(privateKeyValue, curveName);
    }

    public static PrivateKey convertHexedStringToPrivateKey(
            BigInteger privateKey, String curveName) {
        return convertPrivateKeyToKeyPair(privateKey, curveName).getPrivate();
    }

    public static KeyPair convertHexedStringToKeyPair(String hexedPrivateKey, String curveName)
            throws LoadKeyStoreException {
        BigInteger privateKeyValue = new BigInteger(hexedPrivateKey, 16);
        return convertPrivateKeyToKeyPair(privateKeyValue, curveName);
    }

    public static KeyPair convertPrivateKeyToKeyPair(BigInteger privateKey, String curveName)
            throws LoadKeyStoreException {
        try {
            initSecurity();
            org.spongycastle.jce.spec.ECParameterSpec ecParameterSpec =
                    ECNamedCurveTable.getParameterSpec(curveName);
            ECPrivateKeySpec privateKeySpec = new ECPrivateKeySpec(privateKey, ecParameterSpec);
            KeyFactory keyFactory =
                    KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME);
            // get private key
            BCECPrivateKey privateKeyObject =
                    (BCECPrivateKey) keyFactory.generatePrivate(privateKeySpec);
            PublicKey publicKey = getPublicKeyFromPrivateKey(privateKeyObject);
            ECPrivateKeyParameters ecPrivateKeyParameters =
                    new ECPrivateKeyParameters(
                            privateKeyObject.getD(),
                            new ECDomainParameters(
                                    ecParameterSpec.getCurve(),
                                    ecParameterSpec.getG(),
                                    ecParameterSpec.getN()));
            BCECPrivateKey bcecPrivateKey =
                    new BCECPrivateKey(
                            privateKeyObject.getAlgorithm(),
                            ecPrivateKeyParameters,
                            (BCECPublicKey) publicKey,
                            ecParameterSpec,
                            BouncyCastleProvider.CONFIGURATION);
            return new KeyPair(publicKey, bcecPrivateKey);
        } catch (NoSuchProviderException | InvalidKeySpecException | NoSuchAlgorithmException e) {
            throw new LoadKeyStoreException(
                    "covert private key into PrivateKey type failed, "
                            + " error information: "
                            + e.getMessage(),
                    e);
        }
    }

    public static void storePublicKeyWithPem(PrivateKey privateKey, String privateKeyFilePath)
            throws IOException {
        PublicKey publicKey = getPublicKeyFromPrivateKey(privateKey);
        storePublicKeyWithPem(publicKey, privateKeyFilePath);
    }

    public static void storePublicKeyWithPem(PublicKey publicKey, String privateKeyFilePath)
            throws IOException {
        String publicKeyPath = privateKeyFilePath + ".pub";
        PemWriter writer = new PemWriter(new FileWriter(publicKeyPath));
        writer.writeObject(new PemObject("PUBLIC KEY", publicKey.getEncoded()));
        writer.flush();
        writer.close();
    }

    private static Method getMethod(
            Class<EC5Util> ec5UtilClass, String methodName, Class<?>... parameterTypes) {
        try {
            return ec5UtilClass.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            logger.warn("get method for EC5Util failed, method name: {}", methodName);
            return null;
        }
    }

    private static org.spongycastle.jce.spec.ECParameterSpec convertToECParamSpec(
            ECParameterSpec _ecParams) throws LoadKeyStoreException {
        try {
            Class<EC5Util> ec5UtilClass = EC5Util.class;
            String methodName = "convertSpec";
            Object ecParamSpec = null;
            Object ec5utilObject = ec5UtilClass.newInstance();
            Method methodDeclare = getMethod(ec5UtilClass, methodName, ECParameterSpec.class);
            if (methodDeclare != null) {
                ecParamSpec = methodDeclare.invoke(ec5utilObject, _ecParams);

            } else {
                methodDeclare =
                        getMethod(ec5UtilClass, methodName, ECParameterSpec.class, boolean.class);
                if (methodDeclare != null) {
                    ecParamSpec = methodDeclare.invoke(ec5utilObject, _ecParams, false);
                }
            }
            if (ecParamSpec != null) {
                return (org.spongycastle.jce.spec.ECParameterSpec) ecParamSpec;
            }
            logger.error(
                    "convertToECParamSpec exception for {} not found, supported methodList: {}",
                    methodName,
                    (ec5UtilClass.getMethods() != null
                            ? ec5UtilClass.getMethods().toString()
                            : " none"));
            throw new LoadKeyStoreException(
                    "convertToECParamSpec exception for "
                            + methodName
                            + " not found! Please check the version of bcprov-jdk15on!");
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            logger.error(
                    "convertToECParamSpec exception, error: {}, e: {}",
                    e.getMessage(),
                    e.getStackTrace().toString());
            throw new LoadKeyStoreException("convertToECParamSpec exception for " + e.getMessage());
        }
    }

    public static PublicKey getPublicKeyFromPrivateKey(PrivateKey privateKey)
            throws LoadKeyStoreException {
        try {
            initSecurity();
            ECPrivateKey ecPrivateKey = (ECPrivateKey) privateKey;
            ECParameterSpec params = ecPrivateKey.getParams();

            org.spongycastle.jce.spec.ECParameterSpec bcSpec = convertToECParamSpec(params);
            org.spongycastle.math.ec.ECPoint q = bcSpec.getG().multiply(ecPrivateKey.getS());
            org.spongycastle.math.ec.ECPoint bcW =
                    bcSpec.getCurve().decodePoint(q.getEncoded(false));
            ECPoint w =
                    new ECPoint(
                            bcW.getAffineXCoord().toBigInteger(),
                            bcW.getAffineYCoord().toBigInteger());
            ECPublicKeySpec keySpec = new ECPublicKeySpec(w, tryFindNamedCurveSpec(params));
            return (PublicKey)
                    KeyFactory.getInstance("EC", BouncyCastleProvider.PROVIDER_NAME)
                            .generatePublic(keySpec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchProviderException e) {
            String errorMessage =
                    "get publicKey from given the private key failed, error message:"
                            + e.getMessage();
            logger.error(errorMessage);
            throw new LoadKeyStoreException(errorMessage, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static ECParameterSpec tryFindNamedCurveSpec(ECParameterSpec params)
            throws LoadKeyStoreException {
        org.spongycastle.jce.spec.ECParameterSpec bcSpec = convertToECParamSpec(params);
        for (Object name : Collections.list(ECNamedCurveTable.getNames())) {
            ECNamedCurveParameterSpec bcNamedSpec =
                    ECNamedCurveTable.getParameterSpec((String) name);
            if (bcNamedSpec.getN().equals(bcSpec.getN())
                    && bcNamedSpec.getH().equals(bcSpec.getH())
                    && bcNamedSpec.getCurve().equals(bcSpec.getCurve())
                    && bcNamedSpec.getG().equals(bcSpec.getG())) {
                return new ECNamedCurveSpec(
                        bcNamedSpec.getName(),
                        bcNamedSpec.getCurve(),
                        bcNamedSpec.getG(),
                        bcNamedSpec.getN(),
                        bcNamedSpec.getH(),
                        bcNamedSpec.getSeed());
            }
        }
        return params;
    }

    protected abstract PrivateKey getPrivateKey();

    public final String getKeyStoreFile() {
        return this.keyStoreFile;
    }

    /**
     * get keyPair loaded from the keyStore file
     *
     * @return the keyPair
     */
    public KeyPair getKeyPair() {
        PrivateKey privateKey = getPrivateKey();
        PublicKey publicKey = getPublicKeyFromPrivateKey();
        return new KeyPair(publicKey, privateKey);
    }

    protected abstract PublicKey getPublicKey();

    public String getHexedPublicKey() {
        if (!"".equals(hexedPublicKey)) {
            return this.hexedPublicKey;
        }
        this.hexedPublicKey = getHexedPublicKey(getPublicKey());
        return this.hexedPublicKey;
    }

    protected abstract void load(InputStream in);

    /** load information from the keyStoreFile */
    protected void load() {
        try {
            InputStream keyStoreFileInputStream = new FileInputStream(keyStoreFile);
            this.load(keyStoreFileInputStream);
        } catch (FileNotFoundException | org.spongycastle.util.encoders.DecoderException e) {
            String errorMessage =
                    "load keys from "
                            + keyStoreFile
                            + " failed for FileNotFoundException, error message:"
                            + e.getMessage();
            logger.error(errorMessage);
            throw new LoadKeyStoreException(errorMessage, e);
        }
    }

    protected PublicKey getPublicKeyFromPrivateKey() {
        return getPublicKeyFromPrivateKey(getPrivateKey());
    }
}
