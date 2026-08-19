# /// script
# requires-python = ">=3.9"
# dependencies = ["nbtlib"]
# ///
"""Build the Ponder scene worlds -- the little dioramas the scenes animate on top of.

Upstream authors these in-game with Schematic and Quill. Ours are generated
because most of what is in them is *derived*: a checkerboard floor, three belt
runs whose Controller/Index/Length follow from the run's own cells, a vault
whose eight blocks point at their corner. Written by hand that is eight chances
to typo a coordinate; written here it is a loop, and moving a machine one cell
is a one-line diff instead of a re-export of an opaque gzip.

The format is a vanilla 1.21.1 StructureTemplate: gzipped NBT, five keys, no
DataFixer. Ponder's PonderSceneRegistry.loadSchematic reads it with NbtIo.read,
then StructureTemplate.placeInWorld places it -- and calls blockEntity.load(nbt)
only for entries that carry an `nbt` compound. So a block entity with no payload
is exactly the block entity its own constructor builds, which is why almost
nothing here has one. Frequencies, labels, addresses, chest contents and the
packagers' idle animation fields were all baked once and all deleted: a Ponder
world runs no server logic and the scenes script every beat by hand, so none of
it was ever read, let alone drawn.

Three things do not build themselves, and each one is wrong in a way you can
see:

  * Belts. BeltBlock.initBelt returns on its first line when world.isClientSide,
    and a Ponder world is client-side, so a belt never discovers its own chain.
    Controller, Index, Length and IsController are whatever this file says and
    nothing recomputes them. Get one wrong and the belt is not broken, it is
    inert -- it renders, it turns, and no package ever moves along it.
  * The item vault. The only multiblock here, so the only payload that names
    other blocks; wrong pointers load as loose boxes rather than one vault.
  * Signs. Nothing derives sign text, and a sign without it is blank.

Two traps beyond those. An unknown block id loads as air and a misspelled
property name is skipped, both with nothing in the log -- hence STATES, an
allow-list that turns either into a failure here instead of a hole in the
diorama. And a `create:andesite_belt_funnel` with no belt under it is not the
block you wrote: BeltFunnelBlock.updateShape quietly converts it back to a plain
funnel. That one shipped once already, so it is checked.
"""

import gzip
import io
import json
from pathlib import Path

import nbtlib
from nbtlib.tag import Byte, Compound, Int, List, String

OUT = Path(__file__).resolve().parents[1] / "transit/src/main/resources/assets/create_transit/ponder"

DATA_VERSION = 3955       # 1.21.1, from the client jar's version.json world_version

# The one word the border scenes are about. The gate's sign says it and the
# captions say it; one definition, no chance to drift.
LABEL = "depot"

# Every block these scenes use, with every property it has and every legal
# value. Transcribed from assets/*/blockstates/*.json plus the block classes for
# properties the models ignore (waterlogged never changes a model, so it is
# absent from some variant keys while still being a real property).
HORIZONTAL = ("north", "east", "south", "west")
ALL_FACINGS = HORIZONTAL + ("up", "down")
BOOL = ("true", "false")

STATES = {
    "minecraft:white_concrete": {},
    "minecraft:snow_block": {},
    "minecraft:chest": {"facing": HORIZONTAL, "type": ("single", "left", "right"), "waterlogged": BOOL},
    "minecraft:barrel": {"facing": ALL_FACINGS, "open": BOOL},
    "minecraft:oak_wall_sign": {"facing": HORIZONTAL, "waterlogged": BOOL},
    "minecraft:lever": {"face": ("floor", "wall", "ceiling"), "facing": HORIZONTAL, "powered": BOOL},
    "create:packager": {"facing": ALL_FACINGS, "powered": BOOL, "linked": BOOL},
    "create:stock_ticker": {"facing": HORIZONTAL},
    "create:stock_link": {"face": ("floor", "wall", "ceiling"), "facing": HORIZONTAL,
                          "powered": BOOL, "waterlogged": BOOL},
    "create:orange_seat": {"waterlogged": BOOL},
    # HORIZONTAL_AXIS + LARGE and nothing else -- ItemVaultBlock.java:39-49.
    "create:item_vault": {"axis": ("x", "z"), "large": BOOL},
    "create:andesite_casing": {},
    # BeltSlope / BeltPart, and CASING from BeltBlock.
    "create:belt": {"casing": BOOL, "facing": HORIZONTAL, "part": ("start", "middle", "end", "pulley"),
                    "slope": ("horizontal", "upward", "downward", "vertical", "sideways"),
                    "waterlogged": BOOL},
    "create:shaft": {"axis": ("x", "y", "z"), "waterlogged": BOOL},
    "create:cogwheel": {"axis": ("x", "y", "z"), "waterlogged": BOOL},
    "create:large_cogwheel": {"axis": ("x", "y", "z"), "waterlogged": BOOL},
    # BeltFunnelBlock.Shape, all four -- `extended` is real, not a guess.
    "create:andesite_belt_funnel": {"facing": HORIZONTAL, "powered": BOOL, "waterlogged": BOOL,
                                    "shape": ("retracted", "extended", "pushing", "pulling")},
    "create:andesite_scaffolding": {"bottom": BOOL, "distance": tuple(str(d) for d in range(8)),
                                    "waterlogged": BOOL},
    # Ours. transit_link inherits PackagerLinkBlock's four properties untouched;
    # transit_gate is WrenchableDirectionalBlock + POWERED, same as Create's
    # repackager; transit_ticker is HorizontalDirectionalBlock and nothing else.
    "create_transit:transit_link": {"face": ("floor", "wall", "ceiling"), "facing": HORIZONTAL,
                                    "powered": BOOL, "waterlogged": BOOL},
    "create_transit:transit_gate": {"facing": ALL_FACINGS, "powered": BOOL},
    "create_transit:transit_ticker": {"facing": HORIZONTAL},
}


def at(scene, pos, block_id, be=None, **props):
    """Place one block, refusing anything the game would swallow in silence."""
    legal = STATES[block_id]                              # unknown block id
    if set(props) != set(legal):                          # misspelled or forgotten property
        raise KeyError("%s wants exactly %s, got %s" % (block_id, sorted(legal), sorted(props)))
    for name, value in props.items():
        if value not in legal[name]:                      # value the property cannot hold
            raise ValueError("%s[%s=%s] not in %s" % (block_id, name, value, legal[name]))
    if pos in scene:
        raise KeyError("two blocks at %s" % (pos,))
    scene[pos] = (block_id, props, be)


def floor(scene, x0=0, z0=0, size=7, depth=None):
    """Create's checkerboard base plate, verified against stock_link.nbt.

    Arguments mirror scene.configureBasePlate(x0, z0, size), but the floor does
    not have to be square and configureBasePlate does not define it: the
    storyboards reveal their own rectangle rather than calling showBasePlate(),
    and the plate size only decides what the scene rotates and centres around.
    So `depth` is here for the scenes whose machine is wider than it is deep.
    """
    for x in range(x0, x0 + size):
        for z in range(z0, z0 + (size if depth is None else depth)):
            at(scene, (x, 0, z),
               "minecraft:white_concrete" if (x + z) % 2 == 0 else "minecraft:snow_block")


def vault(scene, corner, size=2, length=2):
    """An Item Vault `size` wide and tall and `length` long on the Z axis.

    The only IMultiBlockEntityContainer in these scenes, so the only payload that
    names other blocks: the low corner is the controller and carries Length/Size,
    every other cell points back at it. PonderWorldBlockEntityFix relocates the
    multiblock by the delta between LastKnownPos and where the block actually
    landed, so both are written template-local -- placeInWorld runs at
    BlockPos.ZERO, the delta comes out zero, and the vault stays one vault.
    """
    x0, y0, z0 = corner
    for x in range(x0, x0 + size):
        for y in range(y0, y0 + size):
            for z in range(z0, z0 + length):
                head = (x, y, z) == corner
                be = ('{LastKnownPos: {X: %d, Y: %d, Z: %d}, %sid: "create:item_vault"}'
                      % (x, y, z,
                         "Length: %d, Size: %d, " % (length, size) if head
                         else "Controller: {X: %d, Y: %d, Z: %d}, " % corner))
                at(scene, (x, y, z), "create:item_vault", be, axis="z", large="false")


def sign(text):
    """Sign: four lines per face, each a JSON text component string."""
    def face(lines):
        return Compound({
            "has_glowing_text": Byte(0),
            "color": String("black"),
            # Compact separators to match the exact spelling Create's own scenes use.
            "messages": List[String]([String(json.dumps({"text": line}, separators=(",", ":")))
                                      for line in lines]),
        })
    return Compound({
        "is_waxed": Byte(0),
        "front_text": face(["", text, "", ""]),   # line 2: the gate joins all non-blank lines
        "back_text": face(["", "", "", ""]),
        "id": String("minecraft:sign"),
    })


# -- kinetics --------------------------------------------------------------
# Every belt, cog and motor of one drivetrain shares a Network.Id. Fixed
# literals, never randomised, so regeneration is byte-identical.

NET_GATE = -10445360218156
# The merge scene turns a corner, and a corner is two drivetrains: an X belt and
# a Z belt share no shaft, so they share no network either.
NET_MERGE_IN = -7731905534218
NET_MERGE_OUT = -6402840401384

# Members on the gate scene's one drivetrain: eighteen belt segments, two
# cogwheels and a shaft. Nothing on screen reads it -- there is no stress gauge
# in a Ponder world -- but a network that lies about its own size is the kind of
# detail a later scene would copy.
DRIVETRAIN = 21

# Which way a belt runs is *not* its `facing` property -- facing only orients the
# model. BeltBlockEntity.getMovementFacing derives travel from the sign of Speed:
# on the Z axis a positive Speed moves items south, on the X axis west. So a belt
# whose items should travel along its own facing (index 0 -> L-1, which is how
# every run here is laid out) needs Speed < 0 for north and east, Speed > 0 for
# south and west. All three runs below face north or east, hence one constant.
# Cross-checked against five shipped Create belts, whose Inventory.PositiveOrder
# agrees with this rule in every case.
BELT_SPEED = -16.0

# The storyboards restate this as setKineticSpeed(everywhere(), -16f) at beat 0,
# which overwrites Speed on every KineticBlockEntity directly. The two agree by
# construction; the storyboard's number is the knob to flip after looking at the
# scene in-game, and flipping it reverses all belts in that scene at once.

AXIS = {"north": "z", "south": "z", "east": "x", "west": "x"}


def network(net, size, extra=""):
    return "{Capacity: 262144.0f, Size: %d, Id: %dL, Stress: 0.0f%s}" % (size, net, extra)


def belts(scene, positions, facing, net, size, speed=BELT_SPEED):
    """One belt run, with everything initBelt would have computed baked in.

    initBelt bails on the client, so nothing in a Ponder world ever repairs a
    wrong Controller/Index/Length. Controller is written in schematic-local
    coordinates: PonderSceneRegistry.compile places at BlockPos.ZERO, so
    schematic coords are world coords. (Create's shipped files carry stale
    absolute coords and survive only because BeltBlockEntity.read self-locates
    the controller of the segment whose IsController is set.)
    """
    length = len(positions)
    cx, cy, cz = positions[0]
    # PositiveOrder is BeltInventory's item ordering: true when items run toward
    # higher indices. Derived rather than assumed, because a run laid out from
    # its east or south end has the opposite answer from the north/west ones the
    # other scenes use. getDirectionAwareBeltMovementSpeed is Speed times the
    # facing's own axis direction, negated on X; BeltInventory.tick flips the
    # flag to agree with it on the first tick either way.
    offset = (1 if facing in ("south", "east") else -1) * (-1 if AXIS[facing] == "x" else 1)
    for index, pos in enumerate(positions):
        part = "start" if index == 0 else "end" if index == length - 1 else "middle"
        inv = ", Inventory: {Items: [], PositiveOrder: %db}" % (speed * offset > 0) if index == 0 else ""
        be = ('{Speed: %.1ff, IsController: %db, Length: %d, Network: %s, Index: %d, id: "create:belt", '
              'Controller: {X: %d, Y: %d, Z: %d}, Casing: "NONE", Covered: 0b%s}'
              % (speed, index == 0, length, network(net, size), index, cx, cy, cz, inv))
        at(scene, pos, "create:belt", be, casing="false", facing=facing, part=part,
           slope="horizontal", waterlogged="false")


def cover_belts(scene):
    """What BeltBlock.setCovered would derive, since nothing derives it here.

    A belt whose neighbour above is a full block renders as a closed conveyor:
    BeltBlock.isBlockCoveringBelt says so, and it spares funnels by name, which
    is why the funnels standing on these belts leave them open. BeltModel draws
    that cover as part of the casing, so a covered belt has to be cased too or
    the lid never appears. Both facts are the game's, so they are read off the
    layout here rather than written by hand next to each belt, where moving a
    machine one cell would leave a lid hovering over a bare belt.
    """
    for pos, (block_id, props, be) in list(scene.items()):
        if block_id != "create:belt":
            continue
        above = scene.get((pos[0], pos[1] + 1, pos[2]))
        if above is None or above[0] == "create:andesite_belt_funnel":
            continue
        scene[pos] = (block_id, dict(props, casing="true"),
                      be.replace('Casing: "NONE"', 'Casing: "ANDESITE"').replace("Covered: 0b", "Covered: 1b"))


def cog(net, size, speed=BELT_SPEED):
    return '{Speed: %.1ff, Network: %s, id: "create:simple_kinetic"}' % (speed, network(net, size))


# -- the dioramas --------------------------------------------------------

def scaffold(scene, *positions):
    """The pillar Create stands a machine on when a belt has to pass below it."""
    for pos in positions:
        at(scene, pos, "create:andesite_scaffolding", bottom="false", distance="0",
           waterlogged="false")


def transit_link_scene():
    """Create's own `stock_link.nbt` diorama, with our link in place of theirs.

    Block for block from the shipped file: two packager stacks side by side, a
    chest pair behind one and a 2x2x2 item vault behind the other, a stock
    ticker with a seat, and the andesite casing that carries the lever. No belt,
    no funnel and no drivetrain -- Create's scene has none, which is why its
    checkerboard is whole and why nothing here can point the wrong way.

    Two differences from the shipped file, both deliberate:
      * both `create:stock_link` become `create_transit:transit_link`. Create
        bakes two frequencies, because its scene is about binding a second link
        onto an existing network; ours is about what a link declares, and the
        captions say which network is which.
      * Create's `create:analog_lever` is dropped with the analog-priority beat
        it belongs to, which frees (1,2,3) -- so the lever is baked where
        Create's ends up after its moveSection, on top of the casing, instead
        of floating one cell north waiting to be slid over.
    """
    s = {}
    floor(s)
    at(s, (1, 1, 3), "create:andesite_casing")
    at(s, (1, 2, 3), "minecraft:lever", face="floor", facing="east", powered="false")
    # The default-lane stack: an ordinary chest pair behind an ordinary link.
    at(s, (2, 1, 3), "create:packager", facing="north", powered="false", linked="true")
    at(s, (2, 1, 4), "minecraft:chest", facing="west", type="right", waterlogged="false")
    at(s, (2, 1, 5), "minecraft:chest", facing="west", type="left", waterlogged="false")
    at(s, (2, 2, 3), "create_transit:transit_link",
       face="floor", facing="north", powered="false", waterlogged="false")
    # The named stack: the warehouse the scene's captions name as a border.
    at(s, (4, 1, 1), "create:stock_ticker", facing="east")
    at(s, (4, 1, 3), "create:packager", facing="north", powered="false", linked="true")
    at(s, (4, 2, 3), "create_transit:transit_link",
       face="floor", facing="north", powered="false", waterlogged="false")
    vault(s, (4, 1, 4))
    at(s, (5, 1, 1), "create:orange_seat", waterlogged="false")
    return s


def transit_gate_scene():
    """Three lanes past one buffer, transcribed from the diorama the user built
    in-game and exported with Schematic and Quill.

    Each lane is the same machine: a belt runs east under everything, a funnel
    at x=1 takes arrivals off it into the Item Vault that is the gates' shared
    customs buffer, the gate takes them back out and presents them at its front
    face, and the funnel at x=4 puts them back on the belt to ride off the far
    end. Only the northern gate carries a sign, and that is the lane the
    crossing chapter stages. The other two are scenery for its closing beat --
    gates with no sign of their own, reached by the label on this one -- so
    nothing ever travels their belts and only the northern gate needs a lever.

    Two things the export spells that the eye does not. A belt's `facing` is
    which end it was dragged from, not which way it moves -- getMovementFacing
    reads the axis and the sign of Speed only -- so all three lanes on one
    drivetrain carry items the same way whatever their facings say. And the
    segments under the vault and the gates are cased and covered, because a
    block standing on a belt is what makes BeltBlock cover it; cover_belts()
    below derives that rather than trusting a hand-written flag.
    """
    s = {}
    # Six wide, five deep, exactly as the blueprint laid it: the belts run to
    # x=5 and a plate that stopped at x=4 left their far ends hanging over
    # nothing. configureBasePlate stays 5 -- it only centres the scene.
    floor(s, 0, 1, 6, 5)
    # One drivetrain for the lot: the large cogwheel meshes with the small one a
    # block up and over, the shaft carries it into the first belt's pulley, and
    # the three pulleys pass it along between themselves. All of it stands at
    # x=5 or z=0, off the base plate, the way every shipped scene keeps its
    # drive off the checkerboard.
    at(s, (4, 0, 0), "create:large_cogwheel", cog(NET_GATE, DRIVETRAIN), axis="z", waterlogged="false")
    at(s, (5, 1, 0), "create:cogwheel", cog(NET_GATE, DRIVETRAIN), axis="z", waterlogged="false")
    at(s, (5, 1, 1), "create:shaft", cog(NET_GATE, DRIVETRAIN), axis="z", waterlogged="false")
    for z, facing in ((2, "west"), (3, "east"), (4, "west")):
        run = range(6) if facing == "east" else range(5, -1, -1)
        belts(s, [(x, 1, z) for x in run], facing, NET_GATE, DRIVETRAIN)
        # Facing against the travel, so this one takes off the belt, into the
        # vault behind it.
        at(s, (1, 2, z), "create:andesite_belt_funnel",
           facing="west", powered="false", shape="retracted", waterlogged="false")
        # facing=east puts the gate's storage -- oppositeOfBlockFacing -- in the
        # vault, and its front, where the tray comes out, in the funnel.
        at(s, (3, 2, z), "create_transit:transit_gate", facing="east", powered="false")
        # Same shape, facing with the travel, so it pushes onto the belt.
        at(s, (4, 2, z), "create:andesite_belt_funnel",
           facing="east", powered="false", shape="retracted", waterlogged="false")
    vault(s, (2, 2, 2), size=1, length=3)
    # facing=north means the sign hangs on the block to its south -- the northern gate.
    at(s, (3, 2, 1), "minecraft:oak_wall_sign", sign(LABEL), facing="north", waterlogged="false")
    # The one redstone beat is staged on the signed gate, so it is the only one
    # that needs a lever.
    at(s, (3, 3, 2), "minecraft:lever", face="floor", facing="east", powered="false")
    cover_belts(s)
    return s


def transit_ticker_scene():
    """Two networks, one stack: the ticker is on B, the link above it is on A.

    No warehouse: the scene never reveals one, and a block the storyboard does
    not show is dead weight in the schematic. The ticker still needs something
    to pack from -- it is a Packager underneath and takes its stock from
    oppositeOfBlockFacing -- so a single chest sits in that cell and comes up
    with the ticker.
    """
    s = {}
    floor(s)
    at(s, (1, 1, 3), "create:packager", facing="north", powered="false", linked="true")
    at(s, (1, 1, 4), "minecraft:chest", facing="west", type="right", waterlogged="false")
    at(s, (1, 1, 5), "minecraft:chest", facing="west", type="left", waterlogged="false")
    at(s, (1, 2, 3), "create:stock_link",
       face="floor", facing="north", powered="false", waterlogged="false")
    at(s, (4, 1, 3), "create_transit:transit_ticker", facing="west")
    at(s, (4, 2, 3), "create:stock_link",
       face="floor", facing="north", powered="false", waterlogged="false")
    at(s, (4, 1, 1), "create:stock_ticker", facing="east")
    at(s, (5, 1, 1), "create:orange_seat", waterlogged="false")
    # Nothing behind the ticker on purpose: it lends a whole network out, and a
    # chest there would read as the stock it serves, which is the one thing it
    # is not.
    return s


def transit_merge_scene():
    """Create's own `high_logistics/stock_ticker.nbt`, block for block, with two
    cells changed.

    Transcribed from the shipped schematic rather than from StockTickerScenes,
    which only names positions: two packagers on scaffolding draw from a chest
    and a vault, push north onto a belt running west, and that belt ejects onto
    a second belt running north and off the end. Upstream stands a Stock Ticker
    and a seat beside the exit, which makes the whole thing a shop.

    Two changes, and two of upstream's props dropped -- the trapdoor its belt ran
    out under and the schematicannon its goods were delivered to, neither of
    which a border has any use for. The outbound belt takes the trapdoor's cell
    instead, so what leaves customs rides to the edge and falls off it. Both
    creative motors go too: no scene reveals one -- upstream's own storyboard
    shows the cogwheels and never the motors -- and a Ponder world computes no
    kinetics, so a drivetrain with no source is a drivetrain with one fewer
    block in it. What is left:

      * where the west belt used to eject straight onto the north belt, a funnel
        now lifts the package into a chest, a gate reads that chest, and a second
        funnel puts what the gate hands back onto the north belt. The corner
        becomes customs, which is where a shipment's fragments wait for each
        other;
      * the ticker and its seat become a Transit Ticker with a Transit Link on
        its lid -- what a request crosses through, rather than who asked.

    Two packagers is upstream's own arrangement and happens to be exactly what
    this chapter needs: findPackagersForRequest fans one order out across
    whichever packagers can fill it, so the fragments are packed in two places
    and reach the border apart.

    The two belts run at opposite signs -- +32 west along X, -32 north along Z --
    because they turn a corner, and that is upstream's own doing. A storyboard
    on this diorama therefore cannot open on setKineticSpeed(everywhere()): one
    number would send half the scene backwards.
    """
    s = {}
    floor(s, 0, 0, 7)

    # West along z=4, fed by the packagers. Positive on X is west.
    belts(s, [(x, 1, 4) for x in range(2, 7)], "east", NET_MERGE_IN, 7, speed=32.0)
    at(s, (6, 1, 3), "create:cogwheel", cog(NET_MERGE_IN, 7, 32.0), axis="z", waterlogged="false")
    at(s, (7, 0, 3), "create:large_cogwheel", cog(NET_MERGE_IN, 7, -16.0), axis="z", waterlogged="false")

    # North along x=1, carrying what customs releases to the edge of the plate.
    # One segment longer than upstream's, which stopped short of a trapdoor: a
    # border has nothing to deliver to, so the package rides off the end.
    belts(s, [(1, 1, z) for z in range(6, -1, -1)], "north", NET_MERGE_OUT, 9, speed=-32.0)
    at(s, (2, 1, 6), "create:cogwheel", cog(NET_MERGE_OUT, 9, -32.0), axis="x", waterlogged="false")
    at(s, (2, 0, 7), "create:large_cogwheel", cog(NET_MERGE_OUT, 9, 16.0), axis="x", waterlogged="false")

    # The two packagers, each with its own Stock link, facing north so the
    # funnel in front pushes onto the belt and the storage behind is the chest
    # or the vault.
    scaffold(s, (3, 1, 5), (5, 1, 5), (3, 1, 6), (4, 1, 6), (5, 1, 6))
    for x in (3, 5):
        at(s, (x, 2, 4), "create:andesite_belt_funnel",
           facing="north", powered="false", shape="pushing", waterlogged="false")
        at(s, (x, 2, 5), "create:packager", facing="north", powered="false", linked="true")
        at(s, (x, 3, 5), "create:stock_link",
           face="floor", facing="north", powered="false", waterlogged="false")
    at(s, (3, 2, 6), "minecraft:chest", facing="west", type="single", waterlogged="false")
    vault(s, (4, 2, 6), size=2, length=1)

    # Change one: the corner. A belt funnel outputs behind itself, so facing
    # east lifts what arrives westbound into the barrel to its west; the gate
    # faces north, which puts its storage in that barrel and its tray in the
    # funnel that pushes onto the outbound belt. A barrel rather than a chest
    # because a chest is what the packagers behind pack out of, and the buffer
    # is not stock.
    at(s, (2, 2, 4), "create:andesite_belt_funnel",
       facing="east", powered="false", shape="retracted", waterlogged="false")
    at(s, (1, 2, 4), "minecraft:barrel", facing="up", open="false")
    at(s, (1, 2, 3), "create_transit:transit_gate", facing="north", powered="false")
    at(s, (1, 2, 2), "create:andesite_belt_funnel",
       facing="north", powered="false", shape="pushing", waterlogged="false")

    # Change two: the shop counter becomes the mount a request crosses through.
    # Upstream's seat cell is simply left empty -- a link belongs on a lid.
    at(s, (3, 1, 1), "create_transit:transit_ticker", facing="east")
    at(s, (3, 2, 1), "create_transit:transit_link",
       face="floor", facing="north", powered="false", waterlogged="false")

    cover_belts(s)
    return s


def check_belt_funnels(name, scene):
    """A belt funnel with no belt below it is not the block that was written.

    BeltFunnelBlock.updateShape converts it straight back into a plain funnel the
    first time the game looks down, and the player sees a funnel flapping at bare
    floor. Three of these shipped once; the state itself is legal, so nothing
    between here and the screen had an opinion about it.
    """
    for pos, (block_id, _props, _be) in scene.items():
        if block_id != "create:andesite_belt_funnel":
            continue
        below = scene.get((pos[0], pos[1] - 1, pos[2]))
        assert below is not None and below[0] == "create:belt", \
            "%s: funnel at %s stands on %s, not a belt" % (name, pos, below[0] if below else "air")


def write(path, size, scene):
    """Vanilla StructureTemplate.save's five keys, and nothing else."""
    palette, index, entries = [], {}, []
    # y, then x, then z -- the order StructureTemplate.buildInfoList sorts into,
    # so an unchanged scene regenerates to identical bytes.
    for pos in sorted(scene, key=lambda p: (p[1], p[0], p[2])):
        block_id, props, be = scene[pos]
        key = (block_id, tuple(sorted(props.items())))
        if key not in index:
            state = Compound({"Name": String(block_id)})
            if props:
                # Always strings: NbtUtils.readBlockState calls getString on these.
                state["Properties"] = Compound({k: String(v) for k, v in sorted(props.items())})
            index[key] = len(palette)
            palette.append(state)
        entry = Compound({"pos": List[Int]([Int(c) for c in pos]), "state": Int(index[key])})
        if be is not None:
            entry["nbt"] = nbtlib.parse_nbt(be) if isinstance(be, str) else be
        entries.append(entry)

    raw = io.BytesIO()
    nbtlib.File(Compound({
        "size": List[Int]([Int(c) for c in size]),
        "entities": nbtlib.List([]),          # empty -> subtype End, as vanilla writes
        "blocks": List[Compound](entries),
        "palette": List[Compound](palette),
        "DataVersion": Int(DATA_VERSION),
    })).write(raw)
    # Gzipped by hand rather than via File.save so mtime is pinned. These files
    # are committed, and a gzip header carrying the clock would make every
    # regeneration a diff even when not a byte of NBT changed.
    path.write_bytes(gzip.compress(raw.getvalue(), mtime=0))


SCENES = {
    # name: (size, builder). Sizes are the schematic's own bounds, and they are
    # deliberately larger than the base plate wherever a drivetrain has to go:
    # a block written at y=0 inside the plate *replaces* a checkerboard tile
    # rather than standing on it, so every shipped scene puts its motor in a row
    # the plate does not cover instead.
    "transit_link": ((7, 3, 7), transit_link_scene),
    # The gate's plate is 5x5 at x0-4, z1-5 inside a [6,4,6] schematic: the
    # user's floor is six wide and five deep, which no single basePlateSize can
    # cover, so the column the drivetrain and the belts' far pulleys stand in is
    # the one left off. That is where a package rides off the end, too.
    "transit_gate": ((6, 4, 6), transit_gate_scene),
    "transit_merge": ((8, 4, 8), transit_merge_scene),
    "transit_ticker": ((7, 3, 7), transit_ticker_scene),
}


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    root = Path(__file__).resolve().parents[1]
    for name, (size, build) in SCENES.items():
        scene = build()
        check_belt_funnels(name, scene)
        path = OUT / ("%s.nbt" % name)
        write(path, size, scene)
        print("%s  %d bytes" % (path.relative_to(root), path.stat().st_size))
