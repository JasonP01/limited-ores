/*
 * This file is part of Limited Ores plugin for Mindustry.
 *
 * MIT License
 *
 * Copyright (c) 2025 xpdustry
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.xpdustry.ores;

import arc.Events;
import arc.math.Rand;
import arc.struct.ObjectMap;
import arc.struct.Seq;

import mindustry.Vars;
import mindustry.content.Blocks;
import mindustry.game.EventType;
import mindustry.type.Item;
import mindustry.world.Tile;
import mindustry.world.blocks.production.*;


/**
 * Remainders:
 *  - Save the remaining quantity, of each ore tile, in the map save.
 *  
 *  
 *  Optional:
 *  - Make a uniform quantity distribution between the center and the sides of the veins
 *  - Make this concept also available for liquids.
 *  - Calculate the depletion of liquid sources. (very hard to do =/)
 */
public class LimitedOres {
  public static enum ResourceType { 
    floor, overlay, block 
  }
  
  public static class ResourceTileContent {
    public final Tile tile;
    public final Item item;
    public final ResourceType type;
    public int quantity;
    
    public ResourceTileContent(Tile tile, Item item, ResourceType type, int quantity) {
      this.tile = tile;
      this.item = item;
      this.type = type;
      this.quantity = quantity;
    }
  }
  
  
  public static final transient ObjectMap<Tile, ResourceTileContent> ores = new ObjectMap<>();
  protected static boolean loaded, eventsRegistered;
  protected static final Rand rand = new Rand();
  
  // region initialization
  
  /** Replaces the existing building class from concerned blocks to handle ore vein and liquid sources consumption. */
  public static void init() {
    if (loaded) throw new IllegalStateException("already loaded");
    
    Vars.content.blocks().each(b -> {
      // {@link BurstDrill} first because it inherits from {@link Drill}.
      if (b instanceof BurstDrill d) b.buildType = () -> new BurstDrillBuilding(d);
      else if (b instanceof Drill d) b.buildType = () -> new DrillBuilding(d);
      else if (b instanceof BeamDrill d) b.buildType = () -> new BeamDrillBuilding(d);
      else if (b instanceof WallCrafter d) b.buildType = () -> new WallCrafterBuilding(d);
    });
    
    // Since a tile reuse the same instance for floor, overlay and block, this is the better i can =/
    if (!eventsRegistered) {
      Events.on(EventType.TileChangeEvent.class, e -> {
        if (loaded) tileChanged(e.tile);
      });
      Events.on(EventType.WorldLoadBeginEvent.class, e -> {
        if (loaded) ores.clear();
      });
      //TODO: make an option to determine the ore quantity on the fly?
      Events.on(EventType.WorldLoadEvent.class, e -> {
        Vars.world.tiles.eachTile(LimitedOres::tileChanged);
      });
      
      eventsRegistered = true;
    }
    
    //TODO: register a serialier, or replace the existing, to save the quantity
    
    loaded = true;
  }
  
  /** Replaces the modified building by default ones. */
  public static void uninit() {
    if (!loaded) throw new IllegalStateException("not loaded");
    
    Vars.content.blocks().each(b -> {
      // {@link BurstDrill} first because it inherits from {@link Drill}.
      if (b instanceof BurstDrill d) b.buildType = () -> d.new BurstDrillBuild();
      else if (b instanceof Drill d) b.buildType = () -> d.new DrillBuild();
      else if (b instanceof BeamDrill d) b.buildType = () -> d.new BeamDrillBuild();
      else if (b instanceof WallCrafter d) b.buildType = () -> d.new WallCrafterBuild();
    });
    
    ores.clear();
    
    loaded = false;
  }
  
  // end region
  // region internal

  protected static void tileChanged(Tile tile) {
    ResourceType type = null;
    Item ore = null;
    
    if (tile.floor().itemDrop != null) {
      ore = tile.floor().itemDrop;
      type = ResourceType.floor;
    } else if (tile.overlay() != null && tile.overlay() instanceof mindustry.world.blocks.environment.OreBlock o) {
      ore = o.itemDrop;
      type = ResourceType.overlay;
    } else if (tile.block() != null) { // Currently, only {@link StaticWall}
      ore = tile.wallDrop();
      if (ore == null && tile.block().attributes.get(mindustry.world.meta.Attribute.sand) > 0F) 
        ore = mindustry.content.Items.sand;
      type = ResourceType.block;
    }

    if (ore == null) ores.remove(tile);
    else if (!ores.containsKey(tile)) {
      //TODO: customizable ore type quantity range
      ores.put(tile, new ResourceTileContent(tile, ore, type, rand.random(100, 1000)));
    }
  }
  
  /** Same as {@link #decrementOreQuantity(Tile)} but select a random one. */
  protected static void decrementOreQuantity(Seq<Tile> oreTiles) {
    decrementOreQuantity(oreTiles.random(rand));
  }
  
  /** @param tile must only contains tiles that contains an ore. */
  protected static void decrementOreQuantity(Tile tile) {
    ResourceTileContent ore = ores.getNull(tile);
    if (ore == null) return;
    
    if (ore.quantity-- <= 0) {
      ores.remove(tile);
      //TODO: customizable replace block
      if (ore.type == ResourceType.floor) ore.tile.setFloorNet(Blocks.charr);
      else if (ore.type == ResourceType.overlay) ore.tile.setOverlayNet(Blocks.air);
      else if (ore.type == ResourceType.block) ore.tile.setNet(Blocks.dirtWall);
    }
  }
  
  // end region
  // region custom buildings
  
  public static class DrillBuilding extends Drill.DrillBuild {
    public final Seq<Tile> under;
    private final Drill block;
    
    public DrillBuilding(Drill block) { 
      block.super(); 
      this.block = block;
      this.under = new Seq<>(block.size);
    }
      
    @Override
    public void onProximityUpdate() {
      super.onProximityUpdate();
      under.clear();
      tile.getLinkedTilesAs(block, t -> {
        if (block.canMine(t) && block.getDrop(t) == dominantItem)
          under.add(t);
      });
    }

    @Override
    public void offload(Item item) {
      super.offload(item);
      decrementOreQuantity(under);
    }
  }
  
  public static class BurstDrillBuilding extends BurstDrill.BurstDrillBuild {
    public final Seq<Tile> under;
    private final BurstDrill block;
    
    public BurstDrillBuilding(BurstDrill block) { 
      block.super(); 
      this.block = block;
      this.under = new Seq<>(block.size);
    }
      
    @Override
    public void onProximityUpdate() {
      super.onProximityUpdate();
      under.clear();
      tile.getLinkedTilesAs(block, t -> {
        if (block.canMine(t) && block.getDrop(t) == dominantItem)
          under.add(t);
      });
    }

    @Override
    public void offload(Item item) {
      super.offload(item);
      decrementOreQuantity(under);
    }
  }
  
  public static class BeamDrillBuilding extends BeamDrill.BeamDrillBuild {
    private final BeamDrill block;
    
    public BeamDrillBuilding(BeamDrill block) { 
      block.super(); 
      this.block = block;
    }

    @Override
    public void updateTile() {
      int last = items.total();
      super.updateTile();
      
      int consumed = items.total() - last, n = 0;
      float multiplier = arc.math.Mathf.lerp(1f, block.optionalBoostIntensity, optionalEfficiency);
      float drillTime = block.getDrillTime(lastItem);
      
      if (consumed > 0 && edelta() * multiplier >= drillTime) {
        for (Tile tile : facing) {
          if (tile.wallDrop() != null && n++ < consumed) 
            decrementOreQuantity(tile);
        }
      }
    }
  }
  
  public static class WallCrafterBuilding extends WallCrafter.WallCrafterBuild {
    public final Seq<Tile> facing; 
    private final WallCrafter block;
    
    public WallCrafterBuilding(WallCrafter block) { 
      block.super();
      this.block = block;
      this.facing = new Seq<>(block.size);
    }

    @Override
    public void updateTile() {
      facing.clear();
      float eff = getEfficiency(tile.x, tile.y, rotation, facing::add, null);
      if (shouldConsume() && edelta() * eff >= block.drillTime)
        decrementOreQuantity(facing);
      
      super.updateTile();
    }

    /** from {@link WallCrafter#getEfficiency(int, int, int, Cons, Intc2)} */
    protected float getEfficiency(int tx, int ty, int rotation, arc.func.Cons<Tile> ctile, arc.func.Intc2 cpos) {
      float eff = 0f;
      int cornerX = tx - (block.size-1)/2, cornerY = ty - (block.size-1)/2, s = block.size;

      for (int i=0; i<block.size; i++) {
        int rx = 0, ry = 0;

        switch (rotation) {
          case 0 -> {
            rx = cornerX + s;
            ry = cornerY + i;
          }
          case 1 -> {
            rx = cornerX + i;
            ry = cornerY + s;
          }
          case 2 -> {
            rx = cornerX - 1;
            ry = cornerY + i;
          }
          case 3 -> {
            rx = cornerX + i;
            ry = cornerY - 1;
          }
        }

        if (cpos != null) cpos.get(rx, ry);
        
        Tile other = Vars.world.tile(rx, ry);
        if (other != null && other.solid()) {
          float at = other.block().attributes.get(block.attribute);
          eff += at;
          if (at > 0 && ctile != null) ctile.get(other);
        }
      }
      
      return eff;
    }
  }
}

