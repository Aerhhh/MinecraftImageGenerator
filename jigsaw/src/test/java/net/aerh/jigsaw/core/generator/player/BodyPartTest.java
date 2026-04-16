package net.aerh.jigsaw.core.generator.player;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class BodyPartTest {

    @Test
    void allBodyPartsExist() {
        assertThat(BodyPart.values()).hasSize(6);
    }

    @Test
    void head_dimensions() {
        assertThat(BodyPart.HEAD.width(false)).isEqualTo(8);
        assertThat(BodyPart.HEAD.height()).isEqualTo(8);
        assertThat(BodyPart.HEAD.depth()).isEqualTo(8);
        assertThat(BodyPart.HEAD.halfExtentX(false)).isEqualTo(1.0);
        assertThat(BodyPart.HEAD.halfExtentY()).isEqualTo(1.0);
        assertThat(BodyPart.HEAD.halfExtentZ()).isEqualTo(1.0);
    }

    @Test
    void body_dimensions() {
        assertThat(BodyPart.BODY.width(false)).isEqualTo(8);
        assertThat(BodyPart.BODY.height()).isEqualTo(12);
        assertThat(BodyPart.BODY.depth()).isEqualTo(4);
        assertThat(BodyPart.BODY.halfExtentX(false)).isEqualTo(1.0);
        assertThat(BodyPart.BODY.halfExtentY()).isEqualTo(1.5);
        assertThat(BodyPart.BODY.halfExtentZ()).isEqualTo(0.5);
    }

    @Test
    void classicArm_hasFourPixelWidth() {
        assertThat(BodyPart.RIGHT_ARM.width(false)).isEqualTo(4);
        assertThat(BodyPart.LEFT_ARM.width(false)).isEqualTo(4);
    }

    @Test
    void slimArm_hasThreePixelWidth() {
        assertThat(BodyPart.RIGHT_ARM.width(true)).isEqualTo(3);
        assertThat(BodyPart.LEFT_ARM.width(true)).isEqualTo(3);
    }

    @Test
    void slimArms_haveNarrowerOffset() {
        double classicOffset = Math.abs(BodyPart.RIGHT_ARM.offsetX(false));
        double slimOffset = Math.abs(BodyPart.RIGHT_ARM.offsetX(true));
        assertThat(slimOffset).isLessThan(classicOffset);
    }

    @Test
    void legs_areSymmetricallyPositioned() {
        assertThat(BodyPart.RIGHT_LEG.offsetX(false))
                .isEqualTo(-BodyPart.LEFT_LEG.offsetX(false));
        assertThat(BodyPart.RIGHT_LEG.offsetY())
                .isEqualTo(BodyPart.LEFT_LEG.offsetY());
    }

    @Test
    void head_isAboveBody() {
        assertThat(BodyPart.HEAD.offsetY()).isLessThan(BodyPart.BODY.offsetY());
    }

    @Test
    void legs_areBelowBody() {
        assertThat(BodyPart.RIGHT_LEG.offsetY()).isGreaterThan(BodyPart.BODY.offsetY());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void head_skinModelDoesNotAffectDimensions(boolean slim) {
        assertThat(BodyPart.HEAD.width(slim)).isEqualTo(8);
        assertThat(BodyPart.HEAD.offsetX(slim)).isEqualTo(0);
    }

    @Test
    void legs_areNotAffectedBySlim() {
        assertThat(BodyPart.RIGHT_LEG.width(true)).isEqualTo(BodyPart.RIGHT_LEG.width(false));
        assertThat(BodyPart.LEFT_LEG.width(true)).isEqualTo(BodyPart.LEFT_LEG.width(false));
    }

    @Test
    void uvOrigins_headBase() {
        assertThat(BodyPart.HEAD.baseUvX()).isEqualTo(0);
        assertThat(BodyPart.HEAD.baseUvY()).isEqualTo(0);
    }

    @Test
    void uvOrigins_headOverlay() {
        assertThat(BodyPart.HEAD.overlayUvX()).isEqualTo(32);
        assertThat(BodyPart.HEAD.overlayUvY()).isEqualTo(0);
    }

    @Test
    void uvOrigins_leftArm() {
        assertThat(BodyPart.LEFT_ARM.baseUvX()).isEqualTo(32);
        assertThat(BodyPart.LEFT_ARM.baseUvY()).isEqualTo(48);
    }
}
