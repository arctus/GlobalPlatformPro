/*
 * GlobalPlatformPro - GlobalPlatform tool
 *
 * Copyright (C) 2015-2017 Martin Paljak, martin@martinpaljak.net
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3.0 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
package pro.javacard.gp;

import apdu4j.HexUtils;

import javax.crypto.spec.SecretKeySpec;
import java.security.Key;
import java.util.Arrays;

// Encapsulates a plaintext symmetric key used with GlobalPlatform
public final class GPKey {
    private Type type;
    private int version = 0;
    private int id = 0;
    private int length = -1;
    private byte[] bytes = null;

    // Create a key of given type and given bytes bytes
    public GPKey(byte[] v, Type type) {
        if (v.length != 16 && v.length != 24 && v.length != 32)
            throw new IllegalArgumentException("A valid key must be 16/24/32 bytes long");
        this.bytes = Arrays.copyOf(v, v.length);
        this.length = v.length;
        this.type = type;
    }

    // Raw key, that can be interpreted in any way.
    public GPKey(byte[] key) {
        this(key, Type.RAW);
    }

    // Creates a new key with a new version and id, based on key type and bytes of an existing key
    public GPKey(int version, int id, GPKey other) {
        this(other.bytes, other.type);
        this.version = version;
        this.id = id;
    }

    // Called when parsing KeyInfo template, no values present
    public GPKey(int version, int id, int length, int type) {
        this.version = version;
        this.id = id;
        this.length = length;
        // FIXME: these values should be encapsulated somewhere
        // FIXME: 0x81 is actually reserved according to GP
        if (type == 0x80 || type == 0x81) {
            this.type = Type.DES3;
        } else if (type == 0x88) {
            this.type = Type.AES;
        } else {
            throw new UnsupportedOperationException("Only AES and 3DES are supported currently");
        }
    }

    // Do shuffling as necessary
    private static byte[] resizeDES(byte[] key, int length) {
        if (length == 24) {
            byte[] key24 = new byte[24];
            System.arraycopy(key, 0, key24, 0, 16);
            System.arraycopy(key, 0, key24, 16, 8);
            return key24;
        } else {
            byte[] key8 = new byte[8];
            System.arraycopy(key, 0, key8, 0, 8);
            return key8;
        }
    }

    public int getID() {
        return id;
    }

    public int getVersion() {
        return version;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public int getLength() {
        return length;
    }

    public Type getType() {
        return type;
    }

    // Returns a Java key, usable in Ciphers
    // Only trick here is the size fiddling for DES
    public Key getKeyAs(Type type) {
        if (type == Type.DES) {
            return new SecretKeySpec(resizeDES(bytes, 8), "DES");
        } else if (type == Type.DES3) {
            return new SecretKeySpec(resizeDES(bytes, 24), "DESede");
        } else if (type == Type.AES) {
            return new SecretKeySpec(bytes, "AES");
        }
        throw new IllegalArgumentException("Can only create DES/3DES/AES keys");
    }

    public String toString() {
        StringBuffer s = new StringBuffer();
        s.append("version=" + version);
        s.append(" id=" + id);
        s.append(" type=" + type);
        if (bytes != null)
            s.append(" bytes=" + HexUtils.bin2hex(bytes));
        else
            s.append(" len=" + length);
        byte[] kcv = getKCV();
        if (kcv.length > 0) {
            s.append(" kcv=" + HexUtils.bin2hex(getKCV()));
        }
        return s.toString();
    }

    public byte[] getKCV() {
        if (type == Type.DES3) {
            return GPCrypto.kcv_3des(this);
        } else if (type == Type.AES) {
            return GPCrypto.scp03_key_check_value(this);
        } else {
            return new byte[0];
        }
    }

    // Change the type of a RAW key
    public void become(Type t) {
        if (type != Type.RAW)
            throw new IllegalStateException("Only RAW keys can become a new type");
        type = t;
    }

    public enum Type {
        RAW, DES, DES3, AES
    }
}
