/*
 * Copyright (c) 2021, 117 <https://twitter.com/117scape>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package rs117.hd.data.materials;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.NonNull;
import lombok.Setter;
import net.runelite.api.*;
import rs117.hd.HdPluginConfig;
import rs117.hd.utils.ColorUtils;

public enum Material {
	// - Each enum entry refers to a texture file by name, in lowercase. If a texture with the specified name is found,
	//   it will be loaded and resized to fit the dimensions of the texture array.
	// - Entries that specify a vanillaTextureIndex give names to vanilla textures, and will override the vanilla
	//   texture if a corresponding texture file exists.
	// - Materials can reuse textures by inheriting from a different material.
	// - Materials can be composed of multiple textures by setting texture map fields to materials loaded before it.

	NONE, // Must be the first material
	// Special material used for vanilla textures lacking a material
	VANILLA(NONE, p -> p.setHasTransparency(true)),

	// Special materials
	UNLIT(NONE, p -> p.setUnlit(true)),
	TRANSPARENT,
	LAVA_FLOW_MAP,
	WATER_FLOW_MAP,
	UNDERWATER_FLOW_MAP,
	CAUSTICS_MAP,
	WATER_NORMAL_MAP_1,
	WATER_NORMAL_MAP_2,
	WATER_FOAM,

	// Reserve first 128 materials for vanilla OSRS texture ids
	WOODEN_DOOR_HANDLE(0),
	WATER_FLAT(1),
	BRICK(2),
	WOOD_PLANKS_1(3, p -> p
		.setSpecular(0.35f, 30)),
	LARGE_DOOR(4),
	DARK_WOOD(5),
	ROOF_SHINGLES_1(6, p -> p
		.setSpecular(0.5f, 30)),
	WOODEN_SCREEN(7, p -> p
		.setHasTransparency(true)),
	LEAVES_1(8, p -> p
		.setHasTransparency(true)
		.setTextureScale(1.3f, 1.0f)),
	TREE_RINGS(9, p -> p
		.setHasTransparency(true)),
	MOSS_BRANCH(10),
	CONCRETE(11),
	IRON_BARS(12, p -> p
		.setHasTransparency(true)),
	PAINTING_LANDSCAPE(13),
	PAINTING_KING(14),
	MARBLE_DARK(15, p -> p
		.setSpecular(1.1f, 380)),
	SIMPLE_GRAIN_WOOD(16),
	WATER_DROPLETS(17, p -> p
		.setHasTransparency(true)),
	HAY(18),
	NET(19, p -> p
		.setHasTransparency(true)),
	BOOKCASE(20),
	ROOF_WOODEN_SLATE(21, p -> p
		.setHasTransparency(true)),
	CRATE(22, p -> p
		.setSpecular(0.35f, 30)),
	BRICK_BROWN(23),
	WATER_FLAT_2(24),
	SWAMP_WATER_FLAT(25),
	WEB(26, p -> p
		.setHasTransparency(true)),
	ROOF_SLATE(27),
	MOSS(28, p -> p
		.setHasTransparency(true)),
	TROPICAL_LEAF(29, p -> p
		.setHasTransparency(true)),
	WILLOW_LEAVES(30, p -> p
		.setHasTransparency(true)
		.setTextureScale(1.025f, 1.0f)),
	LAVA(31, p -> p
		.setUnlit(true)
		.setOverrideBaseColor(true)
		.setFlowMap(LAVA_FLOW_MAP, 0.05f, 36, 22)
		.setScroll(0, 1 / 3f)),
	TREE_DOOR_BROWN(32),
	MAPLE_LEAVES(33, p -> p
		.setHasTransparency(true)
		.setTextureScale(1.3f, 1)),
	MAGIC_STARS(34, p -> p
		.setHasTransparency(true)
		.setUnlit(true)
		.setOverrideBaseColor(true)),
	SAND_BRICK(35),
	DOOR_TEXTURE(36),
	BLADE(37),
	SANDSTONE(38),
	PAINTING_ELF(39),
	FIRE_CAPE(40, p -> p
		.setUnlit(true)
		.setOverrideBaseColor(true)
		.setFlowMap(LAVA_FLOW_MAP, 0.05f, 12, 4)
		.setScroll(0, 1 / -3f)),
	LEAVES_2(41, p -> p
		.setHasTransparency(true)
		.setTextureScale(1.1f, 1.1f)),
	MARBLE(42, p -> p
		.setSpecular(1.0f, 400)),
	CLEAN_TILE(43),
	ROOF_SHINGLES_2(44),
	ROOF_BRICK_TILE(45),
	STONE_PATTERN(46),
	TEXTURE_47(47),
	HIEROGLYPHICS(48),
	TEXTURE_49(49),
	ROOF_BRICK_TILE_GREEN(50),
	CLEAN_WOOD_FLOOR(51),
	SNOW_FLAKES(52, p -> p
		.setHasTransparency(true)),
	FROZEN_ABYSSAL_WHIP(53),
	UNUSED_UI_TEXTURE(54),
	ROOF_BRICK_TILE_DARK(55),
	RED_LAVA(56),
	SMOKE_BATTLESTAFF(57),
	UNUSED_LEAVES(58, p -> p
		.setHasTransparency(true)),
	INFERNAL_CAPE(59, p -> p
		.setUnlit(true)
		.setOverrideBaseColor(true)
		.setFlowMap(LAVA_FLOW_MAP, 0.02f, 12, 4)
		.setScroll(0, 0)),
	LEAVES_3(60, p -> p
		.setHasTransparency(true)),
	CLAN_SKULL(61, p -> p
		.setHasTransparency(true)),
	CLAN_PARTYHAT(62, p -> p
		.setHasTransparency(true)),
	CLAN_MAGIC_ICON(63, p -> p
		.setHasTransparency(true)),
	CLAN_MIME_HAPPY(64, p -> p
		.setHasTransparency(true)),
	CLAN_HELMET(65, p -> p
		.setHasTransparency(true)),
	CLAN_SWORDS(66, p -> p
		.setHasTransparency(true)),
	CLAN_MIME_SAD(67, p -> p
		.setHasTransparency(true)),
	CLAN_SKILLING(68, p -> p
		.setHasTransparency(true)),
	CLAN_FARMING(69, p -> p
		.setHasTransparency(true)),
	CLAN_ARROWS(70, p -> p
		.setHasTransparency(true)),
	CLAN_RUNE(71, p -> p
		.setHasTransparency(true)),
	CLAN_THIEVING(72, p -> p
		.setHasTransparency(true)),
	CLAN_BONES(73, p -> p
		.setHasTransparency(true)),
	CLAN_CABBAGE(74, p -> p
		.setHasTransparency(true)),
	CLAN_CAT(75, p -> p
		.setHasTransparency(true)),
	CLAN_COMPASS(76, p -> p
		.setHasTransparency(true)),
	CLAN_FISH(77, p -> p
		.setHasTransparency(true)),
	CLAN_HITPOINTS(78, p -> p
		.setHasTransparency(true)),
	CLAN_PRAYER(79, p -> p
		.setHasTransparency(true)),
	CLAN_HUNTER(80, p -> p
		.setHasTransparency(true)),
	CLAN_RING(81, p -> p
		.setHasTransparency(true)),
	CLAN_ROBINHOOD(82, p -> p
		.setHasTransparency(true)),
	CLAN_FLOWER(83, p -> p
		.setHasTransparency(true)),
	CLAN_DEFENCE(84, p -> p
		.setHasTransparency(true)),
	CLAN_ZAMORAK(85, p -> p
		.setHasTransparency(true)),
	CLAN_GROUP(86, p -> p
		.setHasTransparency(true)),
	CLAN_GROUP_HARDCORE(87, p -> p
		.setHasTransparency(true)),
	CLAN_EMPTY(88, p -> p
		.setHasTransparency(true)),
	SHAYZIEN_LEAVES_1(89, p -> p
		.setHasTransparency(true)),
	SHAYZIEN_LEAVES_2(90, p -> p
		.setHasTransparency(true)
		.setTextureScale(1.1f, 1.1f)),
	WATER_ICE(91),
	SNOW_ROOF(92),
	SMALL_SNOWFLAKES(93),
	COLOR_MAP(94),
	CONCRETE_DARK(95),
	HIEROGLYPHICS_LARGE(96, p -> p
		.setHasTransparency(true)),
	HIEROGLYPHICS_SMALL(97, p -> p
		.setHasTransparency(true)),

	FOG_STATIC(98, p -> p
		.setHasTransparency(true)),
	FOG_VERY_SLOW(99, p -> p
		.setHasTransparency(true)),
	FOG_SLOW(100, p -> p
		.setHasTransparency(true)),
	FOG_MEDIUM(101, p -> p
		.setHasTransparency(true)),
	FOG_FAST(102, p -> p
		.setHasTransparency(true)),
	FOG_VERY_FAST(103, p -> p
		.setHasTransparency(true)),

	FOG_LIGHT_STATIC(104, p -> p
		.setHasTransparency(true)),
	FOG_LIGHT_VERY_SLOW(105, p -> p
		.setHasTransparency(true)),
	FOG_LIGHT_SLOW(106, p -> p
		.setHasTransparency(true)),
	FOG_LIGHT_MEDIUM(107, p -> p
		.setHasTransparency(true)),
	FOG_LIGHT_FAST(108, p -> p
		.setHasTransparency(true)),
	FOG_LIGHT_VERY_FAST(109, p -> p
		.setHasTransparency(true)),

	FOG_HEAVY_STATIC(110, p -> p
		.setHasTransparency(true)),
	FOG_HEAVY_VERY_SLOW(111, p -> p
		.setHasTransparency(true)),
	FOG_HEAVY_SLOW(112, p -> p
		.setHasTransparency(true)),
	FOG_HEAVY_MEDIUM(113, p -> p
		.setHasTransparency(true)),
	FOG_HEAVY_FAST(114, p -> p
		.setHasTransparency(true)),
	FOG_HEAVY_VERY_FAST(115, p -> p
		.setHasTransparency(true)),

	SKULLS(116),
	SKULLS_FOG(117),
	SKULLS_FOG_LIGHT(118),
	SKULLS_FOG_DARK(119),

	WHITE(NONE, p -> {}),
	GRAY_75(NONE, p -> p.setBrightness(ColorUtils.srgbToLinear(.75f))),
	GRAY_65(NONE, p -> p.setBrightness(ColorUtils.srgbToLinear(.65f))),
	GRAY_50(NONE, p -> p.setBrightness(ColorUtils.srgbToLinear(.5f))),
	GRAY_25(NONE, p -> p.setBrightness(ColorUtils.srgbToLinear(.25f))),
	BLACK(NONE, p -> p.setBrightness(0)),

	BLANK_GLOSS(WHITE, p -> p
		.setSpecular(0.9f, 280)),
	BLANK_SEMIGLOSS(WHITE, p -> p
		.setSpecular(0.35f, 80)),

	SNOW_1,
	SNOW_2,
	SNOW_2_DARK(SNOW_2, p -> p.setBrightness(0.5f)),
	SNOW_3,
	SNOW_4,

	GRASS_1,
	GRASS_2,
	GRASS_3,
	GRASS_SCROLLING(GRASS_1, p -> p
		.setScroll(0, 1 / 0.7f)),
	DIRT_1_N,
	DIRT_1(p -> p
		.setNormalMap(DIRT_1_N)
		.setSpecular(0.5f, 35)),
	DIRT_2_N,
	DIRT_2(p -> p
		.setNormalMap(DIRT_2_N)
		.setSpecular(0.4f, 30)),
	GRAVEL_N,
	GRAVEL(p -> p
		.setNormalMap(GRAVEL_N)
		.setSpecular(0.4f, 130)),

	DIRT_1_SHINY(DIRT_1, p -> p
		.setSpecular(1.1f, 380)),
	DIRT_2_SHINY(DIRT_2, p -> p
		.setSpecular(1.1f, 380)),
	GRAVEL_SHINY(GRAVEL, p -> p
		.setSpecular(1.1f, 380)),
	SAND_1_N,
	SAND_1(p -> p
		.setNormalMap(SAND_1_N)
		.setSpecular(0.2f, 10)
	),
	SAND_2_N,
	SAND_2(p -> p
		.setNormalMap(SAND_2_N)
		.setSpecular(0.2f, 10)
	),
	SAND_3_N,
	SAND_3(p -> p
		.setNormalMap(SAND_3_N)
		.setSpecular(0.2f, 10)
	),
	GRUNGE_1,
	GRUNGE_2,
	SUBMERGED_GRUNGE_2(GRUNGE_2, p -> p
		.setFlowMap(UNDERWATER_FLOW_MAP)
		.setFlowMapStrength(0.075f)
		.setFlowMapDuration(new float[] { 12, -12 })),

	ROCK_1_N,
	ROCK_1(p -> p
		.setNormalMap(ROCK_1_N)
		.setSpecular(0.35f, 40)
	),
	ROCK_1_LIGHT(ROCK_1, p -> p.setBrightness(1.4f)),
	ROCK_2_N,
	ROCK_2(p -> p
		.setNormalMap(ROCK_2_N)
		.setSpecular(0.35f, 60)
		.setBrightness(1.2f)
	),

	CARPET,
	FINE_CARPET(CARPET, p -> p
		.setBrightness(1.4f)
		.setTextureScale(0.5f, 0.5f)),

	FALADOR_PATH_BRICK_N,
	FALADOR_PATH_BRICK(p -> p
		.setNormalMap(FALADOR_PATH_BRICK_N)
		.setSpecular(0.3f, 30)
	),
	JAGGED_STONE_TILE_N,
	JAGGED_STONE_TILE(p -> p
		.setNormalMap(JAGGED_STONE_TILE_N)
		.setSpecular(0.5f, 30)
	),

	TILE_SMALL_1(p -> p
		.setSpecular(0.8f, 70)),
	TILES_2X2_1_N,
	TILES_2X2_1(p -> p
		.setNormalMap(TILES_2X2_1_N)),
	TILES_2X2_1_GLOSS(TILES_2X2_1, p -> p
		.setSpecular(1.0f, 70)),
	TILES_2X2_1_SEMIGLOSS(TILES_2X2_1, p -> p
		.setSpecular(0.5f, 300)),
	TILES_2X2_2(p -> p
		.setSpecular(0.3f, 30)),
	TILES_2X2_2_GLOSS(TILES_2X2_2, p -> p
		.setSpecular(1.0f, 70)),
	TILES_2X2_2_SEMIGLOSS(TILES_2X2_2, p -> p
		.setSpecular(0.5f, 300)),

	MARBLE_1,
	MARBLE_2,
	MARBLE_3,
	MARBLE_1_GLOSS(MARBLE_1, p -> p
		.setSpecular(0.9f, 280)),
	MARBLE_2_GLOSS(MARBLE_2, p -> p
		.setSpecular(0.8f, 300)),
	MARBLE_3_GLOSS(MARBLE_3, p -> p
		.setSpecular(0.7f, 320)),
	MARBLE_1_SEMIGLOSS(MARBLE_1, p -> p
		.setSpecular(0.35f, 80)),
	MARBLE_2_SEMIGLOSS(MARBLE_2, p -> p
		.setSpecular(0.3f, 100)),
	MARBLE_3_SEMIGLOSS(MARBLE_3, p -> p
		.setSpecular(0.4f, 120)),

	LASSAR_UNDERCITY_TILE_NORMAL,
	LASSAR_UNDERCITY_TILE_DISP,
	LASSAR_UNDERCITY_TILES(MARBLE_2_SEMIGLOSS, p -> p
		.setNormalMap(LASSAR_UNDERCITY_TILE_NORMAL)
		.setDisplacementMap(LASSAR_UNDERCITY_TILE_DISP)
		.setDisplacementScale(.015f)
	),
	LASSAR_UNDERCITY_TILES_SUBMERGED(LASSAR_UNDERCITY_TILES, p -> p
		.setFlowMap(UNDERWATER_FLOW_MAP)
		.setFlowMapStrength(0.025f)
		.setFlowMapDuration(new float[] { 10, -10 })),

	HD_LAVA_1(p -> p
		.setUnlit(true)
		.setOverrideBaseColor(true)
		.setFlowMap(LAVA_FLOW_MAP, 0.04f, 36, 12)),
	HD_LAVA_2(p -> p
		.setUnlit(true)
		.setOverrideBaseColor(true)
		.setFlowMap(LAVA_FLOW_MAP, 0.04f, 36, 12)),
	HD_MAGMA_1(p -> p
		.setUnlit(true)
		.setOverrideBaseColor(true)
		.setFlowMap(LAVA_FLOW_MAP, 0.04f, 36, 12)),
	HD_MAGMA_2(p -> p
		.setUnlit(true)
		.setOverrideBaseColor(true)
		.setFlowMap(LAVA_FLOW_MAP, 0.04f, 36, 12)),

	BARK_N,
	BARK(p -> p.setNormalMap(BARK_N)),
	LIGHT_BARK(BARK, p -> p.setBrightness(1.75f)),
	WOOD_GRAIN,
	WOOD_GRAIN_2_N,
	WOOD_GRAIN_2(p -> p
		.setNormalMap(WOOD_GRAIN_2_N)
		.setSpecular(0.3f, 30)
	),
	WOOD_GRAIN_2_LIGHT(WOOD_GRAIN_2, p -> p
		.setBrightness(1.1f)
	),
	WOOD_GRAIN_2_WIDE(WOOD_GRAIN_2, p -> p
		.setTextureScale(1.5f, 0.5f)
	),
	WOOD_GRAIN_3,
	DOCK_FENCE,
	DOCK_FENCE_DARK(DOCK_FENCE, p -> p.setBrightness(0.6f)),

	HD_INFERNAL_CAPE(p -> p
		.replaceIf(HdPluginConfig::hdInfernalTexture, INFERNAL_CAPE)
		.setUnlit(true)
		.setOverrideBaseColor(true)
		.setFlowMap(LAVA_FLOW_MAP, 0.02f, 12, 4)
		.setScroll(0, 1 / 3f)),

	HD_BRICK_N,
	HD_BRICK(p -> p
		.replaceIf(HdPluginConfig::modelTextures, BRICK)
		.setNormalMap(HD_BRICK_N)
		.setSpecular(0.4f, 80)
	),
	HD_ROOF_SHINGLES_N,
	HD_ROOF_SHINGLES_1(p -> p
		.replaceIf(HdPluginConfig::modelTextures, ROOF_SHINGLES_1)
		.setSpecular(0.5f, 30)
		.setNormalMap(HD_ROOF_SHINGLES_N)
	),
	HD_MARBLE_DARK(p -> p
		.replaceIf(HdPluginConfig::modelTextures, MARBLE_DARK)
		.setSpecular(1.1f, 380)),
	HD_BRICK_BROWN(p -> p
		.replaceIf(HdPluginConfig::modelTextures, BRICK_BROWN)
		.setNormalMap(HD_BRICK_N)
		.setSpecular(0.4f, 80)
	),
	HD_LAVA_3(p -> p
		.replaceIf(HdPluginConfig::modelTextures, LAVA)
		.setUnlit(true)
		.setOverrideBaseColor(true)
		.setFlowMap(LAVA_FLOW_MAP, 0.05f, 36, 22)
		.setScroll(0, 1 / 3f)),
	HD_ROOF_SHINGLES_2(p -> p
		.replaceIf(HdPluginConfig::modelTextures, ROOF_SHINGLES_2)
		.setSpecular(0.3f, 30)
		.setNormalMap(HD_ROOF_SHINGLES_N)
	),

	WORN_TILES,
	STONE_N,
	STONE,
	STONE_NORMALED(STONE, p -> p
		.setNormalMap(STONE_N)
		.setSpecular(0.3f, 30)
	),
	STONE_LOWGLOSS(STONE, p -> p
		.setSpecular(0.3f, 30)
	),
	STONE_SEMIGLOSS(STONE, p -> p.setSpecular(0.6f, 100)),
	STONE_SCROLLING(STONE, p -> p
		.setScroll(0, -1 / 0.7f)),

	WALL_STONE_N,
	WALL_STONE(p -> p.setNormalMap(WALL_STONE_N)),
	METALLIC_1(p -> p.setSpecular(0.2f, 20)),
	METALLIC_1_SEMIGLOSS(METALLIC_1, p -> p
		.setSpecular(0.3f, 80)),
	METALLIC_1_GLOSS(METALLIC_1, p -> p
		.setSpecular(0.7f, 80)),
	METALLIC_1_HIGHGLOSS(METALLIC_1, p -> p
		.setSpecular(1.1f, 80)),
	METALLIC_2(METALLIC_1, p -> p.setBrightness(1.8f)),
	METALLIC_2_SEMIGLOSS(METALLIC_2, p -> p
		.setSpecular(0.3f, 80)),
	METALLIC_2_GLOSS(METALLIC_2, p -> p
		.setSpecular(0.7f, 80)),
	METALLIC_2_HIGHGLOSS(METALLIC_2, p -> p
		.setSpecular(1.1f, 80)),
	METALLIC_NONE_GLOSS(NONE, p -> p
		.setSpecular(0.7f, 80)),
	WATTLE_1,
	ICE_1(SNOW_4, p -> p
		.replaceIf(HdPluginConfig::winterTheme, WATER_FLAT_2, WATER_FLAT)
		.setSpecular(1.1f, 200)),
	ICE_1_HIGHGLOSS(ICE_1, p -> p
		.replaceIf(HdPluginConfig::winterTheme, WATER_FLAT_2, WATER_FLAT)
		.setSpecular(3.1f, 30)),
	ICE_2(SNOW_2, p -> p
		.setSpecular(1.5f, 800)),
	ICE_3(GRUNGE_2, p -> p
		.setSpecular(1.9f, 1000)),
	ICE_4(WHITE, p -> p
		.setSpecular(1.5f, 1000)
		.setNormalMap(WATER_NORMAL_MAP_2)),
	SLIME_GRUNGE(GRUNGE_1, p -> p
		.setSpecular(4.1f, 60)),
	WATER_PUDDLE(NONE, p -> p
		.setSpecular(1.5f, 80)),
	HD_WOOD_PLANKS_1_N,
	HD_WOOD_PLANKS_1(p -> p
		.setNormalMap(HD_WOOD_PLANKS_1_N)
		.setSpecular(0.3f, 40)
		.setBrightness(1.2f)),
	HD_ROOF_BRICK_TILE_N,
	HD_ROOF_BRICK_TILE(ROOF_BRICK_TILE, p -> p
		.replaceIf(HdPluginConfig::modelTextures, ROOF_BRICK_TILE)
		.setSpecular(0.3f, 30)
		.setNormalMap(HD_ROOF_BRICK_TILE_N)
	),
	HD_ROOF_BRICK_TILE_GREEN(ROOF_BRICK_TILE_GREEN, p -> p
		.replaceIf(HdPluginConfig::modelTextures, ROOF_BRICK_TILE_GREEN)
		.setSpecular(0.3f, 30)
		.setNormalMap(HD_ROOF_BRICK_TILE_N)
	),
	HD_ROOF_BRICK_TILE_DARK(ROOF_BRICK_TILE_DARK, p -> p
		.replaceIf(HdPluginConfig::modelTextures, ROOF_BRICK_TILE_DARK)
		.setSpecular(0.3f, 30)
		.setNormalMap(HD_ROOF_BRICK_TILE_N)
	),
	PLANT_GRUNGE_1(GRUNGE_1, p -> p
		.setSpecular(0.25f, 25)
	),
	PLANT_GRUNGE_2(GRUNGE_2, p -> p
		.setSpecular(0.20f, 20)
	),
	HD_TROPICAL_LEAF(TROPICAL_LEAF, p -> p
		.replaceIf(HdPluginConfig::modelTextures, TROPICAL_LEAF)
	),


	// Seasonal
	WINTER_WILLOW_LEAVES(p -> p
		.replaceIf(HdPluginConfig::winterTheme, WILLOW_LEAVES)
		.setTextureScale(1.025f, 1.0f)),
	WINTER_MAPLE_LEAVES(p -> p
		.replaceIf(HdPluginConfig::winterTheme, MAPLE_LEAVES)
		.setTextureScale(1.3f, 1.0f)),
	WINTER_LEAVES_1(p -> p
		.replaceIf(HdPluginConfig::winterTheme, LEAVES_1)
		.setTextureScale(1.3f, 1.0f)),
	WINTER_LEAVES_2(p -> p
		.replaceIf(HdPluginConfig::winterTheme, LEAVES_2)
		.setTextureScale(1.1f, 1.1f)),
	WINTER_LEAVES_3(p -> p
		.replaceIf(HdPluginConfig::winterTheme, LEAVES_3)),
	WINTER_PAINTING_LANDSCAPE(p -> p
		.replaceIf(HdPluginConfig::winterTheme, PAINTING_LANDSCAPE)),
	WINTER_PAINTING_KING(p -> p
		.replaceIf(HdPluginConfig::winterTheme, PAINTING_KING)),
	WINTER_PAINTING_ELF(p -> p
		.replaceIf(HdPluginConfig::winterTheme, PAINTING_ELF)),
	WINTER_HD_ROOF_SHINGLES_1(p -> p
		.replaceIf(HdPluginConfig::winterTheme, ROOF_SHINGLES_1)
		.setSpecular(0.5f, 30)
		.setNormalMap(HD_ROOF_SHINGLES_N)),
	WINTER_HD_ROOF_SHINGLES_2(p -> p
		.replaceIf(HdPluginConfig::winterTheme, ROOF_SHINGLES_2)
		.setSpecular(0.3f, 30)
		.setNormalMap(HD_ROOF_SHINGLES_N)),
	WINTER_HD_ROOF_BRICK_TILES(p -> p
		.replaceIf(HdPluginConfig::winterTheme, ROOF_BRICK_TILE, ROOF_BRICK_TILE_GREEN, ROOF_BRICK_TILE_DARK)
		.setSpecular(0.3f, 30)
		.setNormalMap(HD_ROOF_BRICK_TILE_N)),
	WINTER_HD_ROOF_SLATE(p -> p
		.replaceIf(HdPluginConfig::winterTheme, ROOF_SLATE)
		.setSpecular(0.5f, 30)),
	WINTER_HD_ROOF_WOODEN_SLATE(p -> p
		.replaceIf(HdPluginConfig::winterTheme, ROOF_WOODEN_SLATE)
		.setSpecular(0.5f, 30)),
	WINTER_JAGGED_STONE_TILE(p -> p
		.setNormalMap(JAGGED_STONE_TILE_N)
		.setSpecular(0.6f, 30)
		.setBrightness(1.4f)),
	WINTER_JAGGED_STONE_TILE_LIGHT(WINTER_JAGGED_STONE_TILE, p -> p
		.setNormalMap(JAGGED_STONE_TILE_N)
		.setSpecular(0.6f, 30)
		.setBrightness(4)),
	WINTER_JAGGED_STONE_TILE_LIGHTER(WINTER_JAGGED_STONE_TILE, p -> p
		.setNormalMap(JAGGED_STONE_TILE_N)
		.setSpecular(0.6f, 30)
		.setBrightness(12)),
	;

	public final Material parent;
	public final Material normalMap;
	public final Material displacementMap;
	public final Material roughnessMap;
	public final Material ambientOcclusionMap;
	public final Material flowMap;
	public final int vanillaTextureIndex;
	public final boolean hasTransparency;
	public final boolean overrideBaseColor;
	public final boolean unlit;
	public final float brightness;
	public final float displacementScale;
	public final float flowMapStrength;
	public final float[] flowMapDuration;
	public final float specularStrength;
	public final float specularGloss;
	public final float[] scrollSpeed;
	public final float[] textureScale;
	public final List<Material> materialsToReplace = new ArrayList<>();
	public final Function<HdPluginConfig, Boolean> replacementCondition;

	@Setter
	private static class Builder {
		private Material parent;
		private Material normalMap;
		private Material displacementMap;
		private Material roughnessMap;
		private Material ambientOcclusionMap;
		private Material flowMap;
		private int vanillaTextureIndex = -1;
		private boolean hasTransparency = false;
		private boolean overrideBaseColor = false;
		private boolean unlit = false;
		private float brightness = 1;
		private float displacementScale = .1f;
		private float flowMapStrength;
		private float[] flowMapDuration = { 0, 0 };
		private float specularStrength;
		private float specularGloss;
		private float[] scrollSpeed = { 0, 0 };
		private float[] textureScale = { 1, 1 };
		private List<Material> materialsToReplace = new ArrayList<>();
		private Function<HdPluginConfig, Boolean> replacementCondition;

		Builder apply(Consumer<Builder> consumer) {
			consumer.accept(this);
			return this;
		}

		Builder setParent(Material parent) {
			this.parent = parent;
			this.normalMap = parent.normalMap;
			this.displacementMap = parent.displacementMap;
			this.roughnessMap = parent.roughnessMap;
			this.ambientOcclusionMap = parent.ambientOcclusionMap;
			this.flowMap = parent.flowMap;
			this.vanillaTextureIndex = parent.vanillaTextureIndex;
			this.hasTransparency = parent.hasTransparency;
			this.overrideBaseColor = parent.overrideBaseColor;
			this.unlit = parent.unlit;
			this.brightness = parent.brightness;
			this.displacementScale = parent.displacementScale;
			this.flowMapStrength = parent.flowMapStrength;
			this.flowMapDuration = parent.flowMapDuration;
			this.specularStrength = parent.specularStrength;
			this.specularGloss = parent.specularGloss;
			this.scrollSpeed = parent.scrollSpeed;
			this.textureScale = parent.textureScale;
			this.materialsToReplace.addAll(parent.materialsToReplace);
			this.replacementCondition = parent.replacementCondition;
			return this;
		}

		Builder setSpecular(float specularStrength, float specularGloss) {
			this.specularStrength = specularStrength;
			this.specularGloss = specularGloss;
			return this;
		}

		Builder setFlowMap(Material flowMap, float flowMapStrength, float durationX, float durationY) {
			this.flowMap = flowMap;
			this.flowMapStrength = flowMapStrength;
			this.flowMapDuration = new float[] { durationX, durationY };
			return this;
		}

		Builder setScroll(float speedX, float speedY) {
			this.scrollSpeed = new float[] { -speedX, -speedY };
			return this;
		}

		Builder setTextureScale(float x, float y) {
			this.textureScale = new float[] { x, y };
			return this;
		}

		Builder replaceIf(@NonNull Function<HdPluginConfig, Boolean> condition, @NonNull Material... materialsToReplace) {
			Collections.addAll(this.materialsToReplace, materialsToReplace);
			this.replacementCondition = condition;
			return this;
		}
	}

	Material() {
		this(p -> {});
	}

	Material(int vanillaTextureIndex) {
		this(p -> p.setVanillaTextureIndex(vanillaTextureIndex));
	}

	Material(Material parent, Consumer<Builder> consumer) {
		this(b -> b.setParent(parent).apply(consumer));
	}

	Material(int vanillaTextureIndex, Consumer<Builder> consumer) {
		this(b -> b.setVanillaTextureIndex(vanillaTextureIndex).apply(consumer));
	}

	Material(Consumer<Builder> consumer) {
		Builder builder = new Builder();
		consumer.accept(builder);
		parent = builder.parent;
		normalMap = builder.normalMap;
		displacementMap = builder.displacementMap;
		roughnessMap = builder.roughnessMap;
		ambientOcclusionMap = builder.ambientOcclusionMap;
		flowMap = builder.flowMap;
		vanillaTextureIndex = builder.vanillaTextureIndex;
		hasTransparency = builder.hasTransparency;
		overrideBaseColor = builder.overrideBaseColor;
		unlit = builder.unlit;
		brightness = builder.brightness;
		displacementScale = builder.displacementScale;
		flowMapStrength = builder.flowMapStrength;
		flowMapDuration = builder.flowMapDuration;
		specularStrength = builder.specularStrength;
		specularGloss = builder.specularGloss;
		scrollSpeed = builder.scrollSpeed;
		textureScale = builder.textureScale;
		materialsToReplace.addAll(builder.materialsToReplace);
		replacementCondition = builder.replacementCondition;
	}

	private static Material[] VANILLA_TEXTURE_MAPPING = {};
	private static final Material[] REPLACEMENT_MAPPING = new Material[Material.values().length];

	public static void updateMappings(Texture[] textures, HdPluginConfig config) {
		var materials = Material.values();
		for (int i = 0; i < materials.length; i++) {
			var material = materials[i];
			// Apply the first successful replacement listed last
			for (int j = materials.length - 1; j >= 0; j--) {
				var replacement = materials[j];
				if (replacement.replacementCondition != null &&
					replacement.replacementCondition.apply(config) &&
					replacement.materialsToReplace.contains(material))
					material = replacement;
			}

			// If the final material is itself a conditional replacement material, and the condition
			// is currently not met, the material won't be loaded, and should be mapped to NONE
			if (material.replacementCondition != null && !material.replacementCondition.apply(config))
				material = NONE;

			REPLACEMENT_MAPPING[i] = material;
		}

		VANILLA_TEXTURE_MAPPING = new Material[textures.length];
		Arrays.fill(VANILLA_TEXTURE_MAPPING, Material.VANILLA);
		for (int i = 0; i < textures.length; i++)
			for (var material : materials)
				if (material.vanillaTextureIndex == i)
					VANILLA_TEXTURE_MAPPING[i] = material.resolveTextureMaterial();
	}

	public static Material fromVanillaTexture(int vanillaTextureId) {
		if (vanillaTextureId < 0 || vanillaTextureId >= VANILLA_TEXTURE_MAPPING.length)
			return VANILLA;
		return VANILLA_TEXTURE_MAPPING[vanillaTextureId].resolveReplacements();
	}

	/**
	 * Returns the final material after all replacements have been made.
	 *
	 * @return the material after resolving all replacements
	 */
	public Material resolveReplacements() {
		return REPLACEMENT_MAPPING[this.ordinal()];
	}

	/**
	 * @return an array of all unique materials in use after all replacements have been accounted for, including NONE.
	 */
	public static Material[] getActiveMaterials() {
		return Arrays.stream(REPLACEMENT_MAPPING)
			.filter(m -> m != VANILLA) // The VANILLA material is used for vanilla textures lacking a material definition
			.distinct()
			.toArray(Material[]::new);
	}

	/**
	 * @return an array of all unique materials in use after all replacements have been accounted for, except NONE.
	 */
	public static Material[] getTextureMaterials() {
		return Arrays.stream(REPLACEMENT_MAPPING)
			.map(Material::resolveTextureMaterial)
			.filter(m -> m != NONE)
			.distinct()
			.toArray(Material[]::new);
	}

	public Material resolveTextureMaterial() {
		var base = this.resolveReplacements();
		while (base.parent != null)
			base = base.parent;
		return base;
	}
}
