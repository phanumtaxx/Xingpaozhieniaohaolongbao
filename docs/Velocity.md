# Velocity

Author: uint32. Ported from Synthetic's Velocity module.

Velocity is registered under Combat. Defaults match Synthetic: Grim V3,
zero horizontal and vertical percentages, explosion handling and wall checks
enabled, six ticks of clipped grace, and 0.18 blocks of phase collision leniency.
All settings are available in its expanded GUI panel.

NCP scales entity motion locally. Grim V3 cancels player motion and queues its
original confirmation sequence for meaningful knockback. Explosion pairs,
confirmation echoes, five incoming packets after an unexpected correction,
and the confirmation feedback circuit retain the reference behavior.

The reference's Grim V3 entity-motion branch does not use horizontal/vertical
percentages, liquid/Elytra restrictions, or motion mode. Its explosion branch
does use the percentages and environment restrictions. These distinctions are
preserved rather than making the settings change a different algorithm.

Packet decisions, scaling, input redirection, collision bounds, phase push
decisions, and correction/phase timers are native C++. Java provides Minecraft
queries, packet transport, lifecycle, settings, and a client-thread receive event.
Incoming packets retain vanilla's thread handoff. Packets replayed on the game
thread are inspected before application; packets completed on the network thread
queue only their movement-state observation afterward.

The movement-state adapter exposes expected-teleport, recent-phase, pearl,
and phase-assist ownership hooks for future modules. PhaseAssist movement
automation and the Phase module themselves are not part of this port.
