package com.wanyuecity.energy.access;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemFaceQualityServiceTest {
    private final SystemFaceQualityService service = new SystemFaceQualityService();

    @Test
    void fallbackRejectsDarkImageWithoutDependingOnDevice() throws Exception {
        Map<String, Object> result = service.check(image(false));
        assertThat(result.get("passed")).isEqualTo(false);
        assertThat(result.get("failedStep")).isEqualTo("TOO_DARK");
    }

    @Test
    void fallbackAllowsDetailedImageForManualReview() throws Exception {
        Map<String, Object> result = service.check(image(true));
        assertThat(result.get("passed")).isEqualTo(true);
        assertThat(result.get("mode")).isEqualTo("SYSTEM_FALLBACK");
        assertThat(result.get("reviewRequired")).isEqualTo(true);
    }

    private byte[] image(boolean checkerboard) throws Exception {
        BufferedImage image = new BufferedImage(500, 500, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int value = checkerboard ? (((x / 8 + y / 8) & 1) == 0 ? 50 : 205) : 5;
                image.setRGB(x, y, new Color(value, value, value).getRGB());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
