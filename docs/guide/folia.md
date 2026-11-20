# Folia Support & Lag Safety

::: info Paper Edition Only
This page describes Folia-specific safety mechanisms in the **Paper edition** of Staff+. The **Velocity edition** does not interact with the game world and does not require Folia safety — it runs entirely on the proxy.
:::

`Staff+` is designed from the ground up to run perfectly on **Folia** multi-threaded servers as well as standard **Paper** and **Spigot** servers. 

If you are running a large server network, you might know that Folia runs different regions of your world on separate threads to prevent lag. This makes standard admin tools crash or lag, but `Staff+` keeps your gameplay completely safe and fast.

Here is how we do it in simple terms:

---

## 1. Asynchronous Teleports (No Server Jumps)
In standard plugins, teleporting a player stops the server for a split second to load chunks. On Folia, this causes crashes. 

`Staff+` uses safe, modern **Asynchronous Teleporting**. The server loads the chunks in the background and moves the player only when the destination is completely ready, keeping your TPS stable at a constant 20.0.

---

## 2. Smart Invisibility (Vanish packet delivery)
When you vanish, your character is hidden from other players by sending packets. On Folia, if these packets are sent incorrectly, other players will still see you or your server will throw errors. 

`Staff+` safely queues and schedules visibility updates on each individual player's tick scheduler, making sure you are **100% invisible** every time you toggle `/vanish`.

---

## 3. Real-time Inventory Inspector (/invsee)
Inspecting player inventories on Folia usually crashes the server if players are located in different regions or worlds, as traditional plugins read foreign player inventory and container states directly from arbitrary threads.

`Staff+` implements **Regional Snapshot Isolation**:
* **Session Registry**: Active inspect sessions are tracked in an in-memory concurrent registry without accessing block-based container states cross-region.
* **1-Tick Deferred Capture**: When a staff member moves items in the GUI, snapshot reads execute safely on the staff viewer's regional scheduler after container clicks resolve.
* **Target Scheduler Synchronization**: The captured snapshot array is dispatched to the target entity's scheduler, ensuring armor, hotbar, and off-hand modifications are thread-confined and fully synchronized with client packet handlers.

---

## 4. Lag-free Spectate Follow (/monitor)
Teleporting a spectating staff member to follow a player on every single tick causes heavy server lag and makes the camera rubber-band or stutter.

`Staff+` implements **Cinematic Follow Damping**:
* **Fewer updates**: Follow checks run every 200 milliseconds instead of 20 times a second, reducing server load by 75%.
* **Smooth prediction**: The camera predicts where the target player is moving and teleports slightly ahead, giving you a smooth, professional view.
* **Tether limit**: Staff can fly freely up to 10 blocks away from the target before being gently pulled back.
