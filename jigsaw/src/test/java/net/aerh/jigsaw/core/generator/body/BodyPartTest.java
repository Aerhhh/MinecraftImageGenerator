package net.aerh.jigsaw.core.generator.body;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BodyPartTest {

    @Test
    void allParts_returnsAllSixBodyParts() {
        List<BodyPart.PartWithGeometry> parts = BodyPart.allParts(SkinModel.CLASSIC);
        assertThat(parts).hasSize(6);
    }

    @ParameterizedTest
    @EnumSource(SkinModel.class)
    void allParts_containsAllBodyPartTypes(SkinModel model) {
        List<BodyPart.PartWithGeometry> parts = BodyPart.allParts(model);
        assertThat(parts).extracting(BodyPart.PartWithGeometry::part)
                .containsExactly(BodyPart.HEAD, BodyPart.BODY, BodyPart.RIGHT_ARM,
                        BodyPart.LEFT_ARM, BodyPart.RIGHT_LEG, BodyPart.LEFT_LEG);
    }

    @Test
    void head_geometryIsConsistent() {
        BodyPart.Geometry geom = BodyPart.HEAD.geometry(SkinModel.CLASSIC);
        assertThat(geom.pixelWidth()).isEqualTo(8);
        assertThat(geom.pixelHeight()).isEqualTo(8);
        assertThat(geom.pixelDepth()).isEqualTo(8);
        assertThat(geom.halfExtentX()).isEqualTo(1.0);
        assertThat(geom.halfExtentY()).isEqualTo(1.0);
        assertThat(geom.halfExtentZ()).isEqualTo(1.0);
    }

    @Test
    void body_geometryIsConsistent() {
        BodyPart.Geometry geom = BodyPart.BODY.geometry(SkinModel.CLASSIC);
        assertThat(geom.pixelWidth()).isEqualTo(8);
        assertThat(geom.pixelHeight()).isEqualTo(12);
        assertThat(geom.pixelDepth()).isEqualTo(4);
        assertThat(geom.halfExtentX()).isEqualTo(1.0);
        assertThat(geom.halfExtentY()).isEqualTo(1.5);
        assertThat(geom.halfExtentZ()).isEqualTo(0.5);
    }

    @Test
    void classicArm_hasFourPixelWidth() {
        BodyPart.Geometry rightArm = BodyPart.RIGHT_ARM.geometry(SkinModel.CLASSIC);
        BodyPart.Geometry leftArm = BodyPart.LEFT_ARM.geometry(SkinModel.CLASSIC);
        assertThat(rightArm.pixelWidth()).isEqualTo(4);
        assertThat(leftArm.pixelWidth()).isEqualTo(4);
    }

    @Test
    void slimArm_hasThreePixelWidth() {
        BodyPart.Geometry rightArm = BodyPart.RIGHT_ARM.geometry(SkinModel.SLIM);
        BodyPart.Geometry leftArm = BodyPart.LEFT_ARM.geometry(SkinModel.SLIM);
        assertThat(rightArm.pixelWidth()).isEqualTo(3);
        assertThat(leftArm.pixelWidth()).isEqualTo(3);
    }

    @Test
    void slimArms_haveNarrowerOffset() {
        double classicOffset = Math.abs(BodyPart.RIGHT_ARM.geometry(SkinModel.CLASSIC).offsetX());
        double slimOffset = Math.abs(BodyPart.RIGHT_ARM.geometry(SkinModel.SLIM).offsetX());
        assertThat(slimOffset).isLessThan(classicOffset);
    }

    @Test
    void legs_areSymmetricallyPositioned() {
        BodyPart.Geometry rightLeg = BodyPart.RIGHT_LEG.geometry(SkinModel.CLASSIC);
        BodyPart.Geometry leftLeg = BodyPart.LEFT_LEG.geometry(SkinModel.CLASSIC);
        assertThat(rightLeg.offsetX()).isEqualTo(-leftLeg.offsetX());
        assertThat(rightLeg.offsetY()).isEqualTo(leftLeg.offsetY());
        assertThat(rightLeg.offsetZ()).isEqualTo(leftLeg.offsetZ());
    }

    @Test
    void head_isAboveBody() {
        BodyPart.Geometry head = BodyPart.HEAD.geometry(SkinModel.CLASSIC);
        BodyPart.Geometry body = BodyPart.BODY.geometry(SkinModel.CLASSIC);
        // Y-down: head has more negative Y (higher up)
        assertThat(head.offsetY()).isLessThan(body.offsetY());
    }

    @Test
    void legs_areBelowBody() {
        BodyPart.Geometry body = BodyPart.BODY.geometry(SkinModel.CLASSIC);
        BodyPart.Geometry rightLeg = BodyPart.RIGHT_LEG.geometry(SkinModel.CLASSIC);
        assertThat(rightLeg.offsetY()).isGreaterThan(body.offsetY());
    }

    @Test
    void head_skinModelDoesNotAffectHeadGeometry() {
        BodyPart.Geometry classic = BodyPart.HEAD.geometry(SkinModel.CLASSIC);
        BodyPart.Geometry slim = BodyPart.HEAD.geometry(SkinModel.SLIM);
        assertThat(classic).isEqualTo(slim);
    }
}
