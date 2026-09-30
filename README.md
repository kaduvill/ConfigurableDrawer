# Configurable Drawer

A drawer for one item type in Minecraft 1.12.2. Set an exact filter and capacity, then store items by hand, through automation, or through a Storage Drawers controller.

Requires Forge, Storage Drawers 5.5.3, and Chameleon.

## Use

- Normal drawer behaviors
- Sneak-right-click with an empty hand to open the drawer settings.
- Set the filter from an item on your cursor or drag one from JEI/HEI. Set the capacity in items.
- Use the **Sides** tab to allow insertion, extraction, both, or neither on each face and on the controller connection.
- Use the **Options** tab to control overflow voiding, the front item icon, and redstone output. Voiding deletes matching items inserted after the drawer is full.

Mining a drawer in survival drops it with its filter, settings, and contents intact.

## Configuration

`config/configurabledrawer.cfg` sets the default capacity for new drawers and the maximum allowed capacity. 
Both default to 65,536 items. Changes require a restart.
