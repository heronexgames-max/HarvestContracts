# HarvestContracts

A daily and weekly crop-contract system for Paper. The inventory board shows current objectives and lets players collect completed weekly rewards.

## Build and install

Run `mvn clean verify` with Java 21. Copy the JAR from `target/` into the `plugins/` folder on Paper 1.21.11. GitHub Actions also packages a JAR when the workflow succeeds.

| Command | Action |
|---|---|
| `/harvest` | View today's crop and progress |
| `/harvest board` | Open the daily and weekly progress board |
| `/harvest claim` | Claim today's daily reward |
| `/harvest weekly` | Claim the oldest available weekly reward |

Daily contracts require 24 mature crops and pay 3 emeralds. The three weekly objectives require 36, 48 and 60 crops and pay 18 emeralds in total. Daily rotations start at midnight UTC and weekly rotations start on Monday UTC. Completed weekly rewards remain claimable after the rotation. Inventory overflow stays available for another claim.

## Demo

Open the board, break an immature crop, then harvest a mature crop matching an objective in Survival. Reopen the board to show the count. Complete the three weekly objectives, try claiming with a full inventory, then clear a slot and collect the remainder. Restart the server and verify that progress and pending weekly rewards persist. The event handler counts only uncancelled block breaks, and daily and weekly progress can advance from the same crop.

## Community harvest

The whole server shares a third weekly challenge. Deliver 256 mature harvests for each of the three featured crops by breaking them in Survival. A harvest counts toward the community pool while that crop still has room. Players who contribute at least 32 qualifying harvests can claim 24 emeralds once all three shared quotas are met. Completed rewards survive the weekly reset and partial claims can be retried. Use the bottom row of `/harvest board` or `/harvest community` to claim.

To demo it, open the board as two players, contribute crops from both accounts, and compare the shared counters with each player's individual eligibility. Check that the personal reward stays locked until the server fills the final quota.
