<img src="transit/src/main/resources/logo.png" alt="Create: Transit" width="180" align="right">

# Create: Transit

[![Modrinth](https://img.shields.io/modrinth/dt/create-transit?logo=modrinth&label=&suffix=%20&style=flat-square&color=242629&labelColor=5CA424&logoColor=1C1C1C)](https://modrinth.com/mod/create-transit)
[![CurseForge](https://img.shields.io/curseforge/dt/1634308?logo=curseforge&label=&suffix=%20&style=flat-square&color=242629&labelColor=F16436&logoColor=1C1C1C)](https://www.curseforge.com/minecraft/mc-mods/create-transit)

Welcome to Create: Transit, advanced logistics for Create.

## Crossing networks

Networks stay sovereign here, and freight moves between them the way it crosses a border. A shipment leaves carrying a transit label naming the gate it has to clear, the gate strips that label off the package as it passes, and what continues on the far side is an ordinary local delivery. Where the borders run, which lanes exist and what is allowed to clear them are yours to lay out.

Transit labels nest, and the first one is what the network reads as the address. Each border strips exactly one layer, so regional hubs, national trunk networks and local depots compose the way real freight does.

### Transit Ticker

Opens one network up to another. Bind the ticker to the network being offered, then mount a link belonging to the network that should reach it: with a Stock Link the whole of its stock serves that network's requests, as if the two were one warehouse; with a Transit Link it becomes foreign territory instead, reached through a border. Access runs one way — the network offered up cannot reach back.

### Transit Link

Declares a border. Every shipment it forwards leaves stamped with its transit label, and an unnamed link stamps the default lane — so it works the moment you place it. Full redstone takes it out of service, as with any Stock Link.

### Transit Gate

Customs. Strips one transit label from each package passing through, and holds the parts of a declared order until they can leave merged as a single delivery.

## Train dispatch

Transit freight moves by rail only on trains carrying a **Transport Timetable**. The timetable donates a train to the network — bind its parking bay, hand it to a conductor — and the dispatcher does the rest.

## Related

**[Create: Routes](route/README.md)** — Write once, follow anywhere.

## Development

[DEVELOPMENT.md](DEVELOPMENT.md)
