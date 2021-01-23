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
package org.fisco.bcos.sdk.crypto.signature;

import org.fisco.bcos.sdk.crypto.exceptions.SignatureException;
import org.fisco.bcos.sdk.rlp.RlpString;
import org.fisco.bcos.sdk.rlp.RlpType;
import org.fisco.bcos.sdk.utils.ByteUtils;
import org.fisco.bcos.sdk.utils.Hex;

import java.util.List;

public abstract class SignatureResult {
    protected byte[] r;
    protected byte[] s;
    protected byte[] signatureBytes;

    SignatureResult(final byte[] r, final byte[] s) {
        this.r = r;
        this.s = s;
    }

    /**
     * Recover v, r, s from signature string The first 32 bytes are r, and the 32 bytes after r are
     * s
     *
     * @param signatureString the signatureString
     */
    SignatureResult(final String signatureString) {
        this.signatureBytes = Hex.decode(signatureString);
        // at least 64 bytes
        if (this.signatureBytes.length < 64) {
            throw new SignatureException(
                    "Invalid signature: "
                            + signatureString
                            + ", signatureString len: "
                            + signatureString.length()
                            + ", signatureBytes size:"
                            + signatureBytes.length);
        }
        // get R
        this.r = new byte[32];
        System.arraycopy(this.signatureBytes, 0, this.r, 0, 32);
        // get S
        this.s = new byte[32];
        System.arraycopy(this.signatureBytes, 32, this.s, 0, 32);
    }

    public byte[] getR() {
        return r;
    }

    public void setR(byte[] r) {
        this.r = r;
    }

    public byte[] getS() {
        return s;
    }

    public void setS(byte[] s) {
        this.s = s;
    }

    public byte[] getSignatureBytes() {
        return signatureBytes;
    }

    public void setSignatureBytes(byte[] signatureBytes) {
        this.signatureBytes = signatureBytes;
    }

    protected void encodeCommonField(List<RlpType> encodeResult) {
        encodeResult.add(RlpString.create(ByteUtils.trimLeadingZeroes(this.getR())));
        encodeResult.add(RlpString.create(ByteUtils.trimLeadingZeroes(this.getS())));
    }

    /**
     * covert signatureResult into String
     *
     * @return signatureResult in string form can be used as a verify parameter
     */
    public abstract String convertToString();

    /**
     * encode the signatureResult into rlp-list
     *
     * @return the encoded rlp-list with r, s, v( or pub)
     */
    public abstract List<RlpType> encode();
}
