package net.aerh.jigsaw.api.text;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class ChatColorTest {

    // Test 1: lookupByCode('a') returns GREEN
    @Test
    void byCode_a_returnsGreen() {
        ChatColor result = ChatColor.byCode('a');
        assertThat(result).isEqualTo(ChatColor.GREEN);
    }

    // Test 2: lookupByName("green") returns GREEN (case insensitive)
    @Test
    void byName_green_caseInsensitive_returnsGreen() {
        assertThat(ChatColor.byName("green")).isEqualTo(ChatColor.GREEN);
        assertThat(ChatColor.byName("GREEN")).isEqualTo(ChatColor.GREEN);
        assertThat(ChatColor.byName("Green")).isEqualTo(ChatColor.GREEN);
    }

    // Test 3: Colors are correct RGB values
    @Test
    void colors_correctRgbValues() {
        assertThat(ChatColor.BLACK.color()).isEqualTo(new Color(0, 0, 0));
        assertThat(ChatColor.DARK_BLUE.color()).isEqualTo(new Color(0, 0, 170));
        assertThat(ChatColor.DARK_GREEN.color()).isEqualTo(new Color(0, 170, 0));
        assertThat(ChatColor.DARK_AQUA.color()).isEqualTo(new Color(0, 170, 170));
        assertThat(ChatColor.DARK_RED.color()).isEqualTo(new Color(170, 0, 0));
        assertThat(ChatColor.DARK_PURPLE.color()).isEqualTo(new Color(170, 0, 170));
        assertThat(ChatColor.GOLD.color()).isEqualTo(new Color(255, 170, 0));
        assertThat(ChatColor.GRAY.color()).isEqualTo(new Color(170, 170, 170));
        assertThat(ChatColor.DARK_GRAY.color()).isEqualTo(new Color(85, 85, 85));
        assertThat(ChatColor.BLUE.color()).isEqualTo(new Color(85, 85, 255));
        assertThat(ChatColor.GREEN.color()).isEqualTo(new Color(85, 255, 85));
        assertThat(ChatColor.AQUA.color()).isEqualTo(new Color(85, 255, 255));
        assertThat(ChatColor.RED.color()).isEqualTo(new Color(255, 85, 85));
        assertThat(ChatColor.LIGHT_PURPLE.color()).isEqualTo(new Color(255, 85, 255));
        assertThat(ChatColor.YELLOW.color()).isEqualTo(new Color(255, 255, 85));
        assertThat(ChatColor.WHITE.color()).isEqualTo(new Color(255, 255, 255));
    }

    // Test 4: Unknown code returns null
    @Test
    void byCode_unknownCode_returnsNull() {
        assertThat(ChatColor.byCode('z')).isNull();
        assertThat(ChatColor.byCode('!')).isNull();
    }

    @Test
    void byName_unknownName_returnsNull() {
        assertThat(ChatColor.byName("purple")).isNull();
        assertThat(ChatColor.byName("notacolor")).isNull();
    }

    @Test
    void chatColor_hasSixteenEntries() {
        assertThat(ChatColor.values()).hasSize(16);
    }

    @Test
    void chatColor_codes_matchExpected() {
        assertThat(ChatColor.BLACK.code()).isEqualTo('0');
        assertThat(ChatColor.DARK_BLUE.code()).isEqualTo('1');
        assertThat(ChatColor.DARK_GREEN.code()).isEqualTo('2');
        assertThat(ChatColor.DARK_AQUA.code()).isEqualTo('3');
        assertThat(ChatColor.DARK_RED.code()).isEqualTo('4');
        assertThat(ChatColor.DARK_PURPLE.code()).isEqualTo('5');
        assertThat(ChatColor.GOLD.code()).isEqualTo('6');
        assertThat(ChatColor.GRAY.code()).isEqualTo('7');
        assertThat(ChatColor.DARK_GRAY.code()).isEqualTo('8');
        assertThat(ChatColor.BLUE.code()).isEqualTo('9');
        assertThat(ChatColor.GREEN.code()).isEqualTo('a');
        assertThat(ChatColor.AQUA.code()).isEqualTo('b');
        assertThat(ChatColor.RED.code()).isEqualTo('c');
        assertThat(ChatColor.LIGHT_PURPLE.code()).isEqualTo('d');
        assertThat(ChatColor.YELLOW.code()).isEqualTo('e');
        assertThat(ChatColor.WHITE.code()).isEqualTo('f');
    }
}
