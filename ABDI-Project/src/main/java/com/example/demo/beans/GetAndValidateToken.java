package com.example.demo.beans;

import java.text.ParseException;
import java.util.Date;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

@Service
public class GetAndValidateToken {
	
	private RSAKey rsaPublicJWK;

	public String getToken() throws JOSEException {
		// RSA signatures require a public and private RSA key pair, the public key 
		// must be made known to the JWS recipient in order to verify the signatures
		RSAKey rsaJWK = new RSAKeyGenerator(2048).keyID("1234").generate();
		rsaPublicJWK = rsaJWK.toPublicJWK();
		System.out.println("rsaPublicKey:" + rsaPublicJWK.toString());
		
		//Create RSA-signer with the private key
		JWSSigner signer = new RSASSASigner(rsaJWK);
		
		// Prepare JWT with claims set
		int expireTime = 600;
		
		JWTClaimsSet claimsSet = new JWTClaimsSet.Builder().
				expirationTime(new Date(new Date().getTime() + expireTime * 1000)).
				subject("7255").
				jwtID(UUID.randomUUID().toString()).issuer("ABDI").
				build();
		
		SignedJWT signedJWT = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).
				keyID(rsaJWK.getKeyID()).build(), claimsSet);
		
		//compute the RSA signature
		signedJWT.sign(signer);
		
		// To serialize to compact form, produces something like
		// eyJhbGciOiJSUzI1NiJ9.SW4gUlNBIHdlIHRydXN0IQ.IRMQENi4nJyp4er2L
		// mZq3ivwoAjqa1uUkSBKFIX7ATndFF5ivnt-m8uApHO4kfIFOrW7w2Ezmlg3Qd
		// maXlS9DhN0nUk_hGI3amEjkKd0BWYCB8vfUbUv0XGjQip78AI4z1PrFRNidm7
		// -jPDm5Iq0SZnjKjCNS5Q15fokXZc8u0A
		String token = signedJWT.serialize(); //base64encoding
		
		return token;
		
	}
	
	
	
	public String authorizeToken(@RequestHeader HttpHeaders headers) throws ParseException, JOSEException {
		String token = headers.getFirst("Authorization");
		if (token == null || token.isEmpty()) {
			return "No token Found";
		}
		
		if(rsaPublicJWK==null) {
			return "Token is invalid";
		}

		if (!token.contains("Bearer ")) {
			return "Improper Format of Token";
		}
		
		String token1 = "";
		token1 = token.substring(7);

		boolean authorized = ifAuthorized(token1);

		if (authorized == false) {
			return "Token is Expired or Invalid Token";
		}
		return "Valid Token";
	}
	
	
	private boolean ifAuthorized(String token) throws ParseException, JOSEException {
		// On the consumer side, parse the JWS and verify its RSA signature
		SignedJWT signedJWT = SignedJWT.parse(token);
		
		
		JWSVerifier verifier = new RSASSAVerifier(rsaPublicJWK);
		// Retrieve / verify the JWT claims according to the app requirements
		if (!signedJWT.verify(verifier)) {
			return false;
		}
		
		JWTClaimsSet claimSet = signedJWT.getJWTClaimsSet();
		Date exp = claimSet.getExpirationTime();
		
		return new Date().before(exp);
	}
	
	
}
