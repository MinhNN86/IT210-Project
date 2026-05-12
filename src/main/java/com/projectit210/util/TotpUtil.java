package com.projectit210.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Utility class for TOTP (Time-based One-Time Password) operations.
 * Uses GoogleAuth library for generating and verifying TOTP codes,
 * and ZXing for QR code generation.
 */
@Component
public class TotpUtil {

    private static final String ISSUER = "Smart Academic Platform";
    private final GoogleAuthenticator googleAuthenticator;

    public TotpUtil() {
        this.googleAuthenticator = new GoogleAuthenticator();
    }

    /**
     * Generate a new TOTP secret key for a user.
     *
     * @return the secret key string
     */
    public String generateSecret() {
        GoogleAuthenticatorKey key = googleAuthenticator.createCredentials();
        return key.getKey();
    }

    /**
     * Verify a TOTP code against a user's secret.
     *
     * @param secret the user's TOTP secret
     * @param code   the TOTP code to verify
     * @return true if the code is valid
     */
    public boolean verifyCode(String secret, int code) {
        return googleAuthenticator.authorize(secret, code);
    }

    /**
     * Build the otpauth:// URL for QR code scanning.
     * Format: otpauth://totp/ISSUER:USERNAME?secret=SECRET&issuer=ISSUER
     *
     * @param username the user's username
     * @param secret   the TOTP secret
     * @return the otpauth URL
     */
    public String getOtpAuthUrl(String username, String secret) {
        try {
            String encodedIssuer = URLEncoder.encode(ISSUER, StandardCharsets.UTF_8.name());
            String encodedUsername = URLEncoder.encode(username, StandardCharsets.UTF_8.name());
            return String.format(
                    "otpauth://totp/%s:%s?secret=%s&issuer=%s&algorithm=SHA1&digits=6&period=30",
                    encodedIssuer, encodedUsername, secret, encodedIssuer
            );
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Failed to build otpauth URL", e);
        }
    }

    /**
     * Generate a QR code image as a Base64-encoded string.
     *
     * @param otpAuthUrl the otpauth:// URL to encode
     * @param width      image width in pixels
     * @param height     image height in pixels
     * @return Base64-encoded PNG image string
     */
    public String generateQrCodeBase64(String otpAuthUrl, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(otpAuthUrl, BarcodeFormat.QR_CODE, width, height);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}
