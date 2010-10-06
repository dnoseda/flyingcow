package com.plugin.etagFilter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class ETagComputeUtils {
	public static byte[] serialize(Object obj) throws IOException {
	 	byte[] byteArray = null;
	 	ByteArrayOutputStream baos = null;
	 	ObjectOutputStream out = null;
	 	try {
	 		// These objects are closed in the finally.
	 		baos = new ByteArrayOutputStream();
	 		out = new ObjectOutputStream(baos);
	 		out.writeObject(obj);
	 		byteArray = baos.toByteArray();
	 	} finally {
	 		if (out != null) {
	 			out.close();
	 		}
	 	}
	 	return byteArray;
	 }

	  public static String getMd5Digest(byte[] bytes) {
	 	MessageDigest md;
	 	try {
	 		md = MessageDigest.getInstance("MD5");
	 	} catch (NoSuchAlgorithmException e) {
	 		throw new RuntimeException("MD5 cryptographic algorithm is not available.", e);
	 	}
	 	byte[] messageDigest = md.digest(bytes);
	 	BigInteger number = new BigInteger(1, messageDigest);
	 	// prepend a zero to get a "proper" MD5 hash value
	 	StringBuffer sb = new StringBuffer('0');
	 	sb.append(number.toString(16));
	 	return sb.toString();
	 }
}
