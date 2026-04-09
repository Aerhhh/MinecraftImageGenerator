package net.aerh.jigsaw.core.generator;

import net.aerh.jigsaw.core.generator.body.ArmorSlot;
import net.aerh.jigsaw.core.generator.body.SkinModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlayerBodyRequestTest {

    @Test
    void fromBase64_createsRequestWithBase64Texture() {
        PlayerBodyRequest request = PlayerBodyRequest.fromBase64("someBase64Value").build();
        assertThat(request.base64Texture()).contains("someBase64Value");
        assertThat(request.textureUrl()).isEmpty();
        assertThat(request.scale()).isEqualTo(1);
        assertThat(request.skinModel()).isEqualTo(SkinModel.CLASSIC);
    }

    @Test
    void fromUrl_createsRequestWithTextureUrl() {
        PlayerBodyRequest request = PlayerBodyRequest.fromUrl("http://example.com/skin.png").build();
        assertThat(request.textureUrl()).contains("http://example.com/skin.png");
        assertThat(request.base64Texture()).isEmpty();
    }

    @Test
    void requiresAtLeastOneSource() {
        assertThatThrownBy(() -> new PlayerBodyRequest(
                Optional.empty(), Optional.empty(), Optional.empty(),
                SkinModel.CLASSIC, 0, 0, 0, List.of(), List.of(), 1
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
    }

    @Test
    void scaleMinimumIsOne() {
        assertThatThrownBy(() -> PlayerBodyRequest.fromUrl("http://example.com/skin.png")
                .scale(0)
                .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale must be >= 1");
    }

    @Test
    void scaleMaximumIs64() {
        assertThatThrownBy(() -> PlayerBodyRequest.fromUrl("http://example.com/skin.png")
                .scale(65)
                .build()
        ).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale must be <= 64");
    }

    @Test
    void builderPreservesAllFields() {
        PlayerBodyRequest request = PlayerBodyRequest.fromBase64("b64data")
                .textureUrl("http://example.com/skin.png")
                .playerName("Notch")
                .skinModel(SkinModel.SLIM)
                .xRotation(1.0)
                .yRotation(2.0)
                .zRotation(3.0)
                .scale(4)
                .build();

        assertThat(request.base64Texture()).contains("b64data");
        assertThat(request.textureUrl()).contains("http://example.com/skin.png");
        assertThat(request.playerName()).contains("Notch");
        assertThat(request.skinModel()).isEqualTo(SkinModel.SLIM);
        assertThat(request.xRotation()).isEqualTo(1.0);
        assertThat(request.yRotation()).isEqualTo(2.0);
        assertThat(request.zRotation()).isEqualTo(3.0);
        assertThat(request.scale()).isEqualTo(4);
    }

    @Test
    void defaultRotationsAreIsometric() {
        PlayerBodyRequest request = PlayerBodyRequest.fromUrl("http://example.com/skin.png").build();
        assertThat(request.xRotation()).isEqualTo(Math.PI / 6);
        assertThat(request.yRotation()).isEqualTo(-Math.PI / 4);
        assertThat(request.zRotation()).isEqualTo(0);
    }

    @Test
    void armorPiecesListIsImmutable() {
        PlayerBodyRequest request = PlayerBodyRequest.fromUrl("http://example.com/skin.png").build();
        assertThatThrownBy(() -> request.armorPieces().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void withInheritedScale_preservesExistingScale() {
        PlayerBodyRequest request = PlayerBodyRequest.fromUrl("http://example.com/skin.png")
                .scale(4)
                .build();
        assertThat(request.withInheritedScale(8)).isSameAs(request);
    }

    @Test
    void withInheritedScale_appliesWhenScaleIsOne() {
        PlayerBodyRequest request = PlayerBodyRequest.fromUrl("http://example.com/skin.png").build();
        PlayerBodyRequest inherited = (PlayerBodyRequest) request.withInheritedScale(8);
        assertThat(inherited.scale()).isEqualTo(8);
    }

    @Test
    void nullSkinModelThrows() {
        assertThatThrownBy(() -> new PlayerBodyRequest(
                Optional.empty(), Optional.of("http://example.com/skin.png"), Optional.empty(),
                null, 0, 0, 0, List.of(), List.of(), 1
        )).isInstanceOf(NullPointerException.class);
    }

    @Test
    void armorByMaterial_addsToArmorMaterials() {
        PlayerBodyRequest request = PlayerBodyRequest.fromUrl("http://example.com/skin.png")
                .armor(ArmorSlot.HELMET, "iron")
                .armor(ArmorSlot.LEGGINGS, "iron")
                .build();
        assertThat(request.armorMaterials()).hasSize(2);
        assertThat(request.armorMaterials().get(0).slot()).isEqualTo(ArmorSlot.HELMET);
        assertThat(request.armorMaterials().get(0).material()).isEqualTo("iron");
    }

    @Test
    void armorByMaterialDyed_preservesDyeColor() {
        PlayerBodyRequest request = PlayerBodyRequest.fromUrl("http://example.com/skin.png")
                .armor(ArmorSlot.CHESTPLATE, "leather", 0xFFA06540)
                .build();
        assertThat(request.armorMaterials()).hasSize(1);
        assertThat(request.armorMaterials().get(0).dyeColor()).contains(0xFFA06540);
    }

    @Test
    void armorMaterialsList_isImmutable() {
        PlayerBodyRequest request = PlayerBodyRequest.fromUrl("http://example.com/skin.png")
                .armor(ArmorSlot.HELMET, "iron")
                .build();
        assertThatThrownBy(() -> request.armorMaterials().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
