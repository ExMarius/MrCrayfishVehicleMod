# Paritate cu modul Java — generat de `horizon/tools/parity.py`

Clase Java in sursa: **313**.

| Stare | Clase | Inseamna |
|---|---|---|
| `ported` | 3 | logica e in Rust |
| `partial` | 2 | o parte e in Rust |
| `render` | 56 | acoperita de `native_mob_submit` + modelele importate |
| `data` | 2 | continut importat in `.hmod` |
| `todo` | 250 | neinceput |

Starea se da in `horizon/mod/parity.json`; restul vine din `PACKAGE_DEFAULTS`
din script. Nu modifica tabelul de mai jos de mana — ruleaza scriptul.

## `com.mrcrayfish.framework.client.model`

| Clasa | Stare |
|---|---|
| `OpenModel` | todo |

## `com.mrcrayfish.vehicle`

| Clasa | Stare |
|---|---|
| `Config` | todo |
| `Reference` | todo |
| `VehicleMod` | todo |

## `com.mrcrayfish.vehicle.block`

| Clasa | Stare |
|---|---|
| `BoostPadBlock` | todo |
| `BoostRampBlock` | todo |
| `FluidExtractorBlock` | todo |
| `FluidMixerBlock` | todo |
| `FluidPipeBlock` | todo |
| `FluidPumpBlock` | todo |
| `FuelDrumBlock` | todo |
| `GasPumpBlock` | todo |
| `IndustrialFuelDrumBlock` | todo |
| `JackBlock` | todo |
| `JackHeadBlock` | todo |
| `ObjectBlock` | todo |
| `RotatedObjectBlock` | todo |
| `SteepBoostRampBlock` | todo |
| `TrafficConeBlock` | todo |
| `VehicleCrateBlock` | todo |
| `WorkstationBlock` | todo |

## `com.mrcrayfish.vehicle.client`

| Clasa | Stare |
|---|---|
| `CameraHelper` | todo |
| `CameraProperties` | todo |
| `ClientEvents` | todo |
| `ClientHandler` | todo |
| `CosmeticCache` | todo |
| `KeyBinds` | todo |
| `VehicleHelper` | todo |

## `com.mrcrayfish.vehicle.client.audio`

| Clasa | Stare |
|---|---|
| `MovingEngineSound` | todo |
| `MovingHornSound` | todo |

## `com.mrcrayfish.vehicle.client.event`

| Clasa | Stare |
|---|---|
| `VehicleRayTraceEvent` | todo |

## `com.mrcrayfish.vehicle.client.handler`

| Clasa | Stare |
|---|---|
| `CameraHandler` | todo |
| `ControllerHandler` | todo |
| `FuelingHandler` | todo |
| `HeldVehicleHandler` | todo |
| `InputHandler` | todo |
| `OverlayHandler` | todo |
| `PlayerModelHandler` | todo |
| `SprayCanHandler` | todo |

## `com.mrcrayfish.vehicle.client.model`

| Clasa | Stare |
|---|---|
| `ComponentLoader` | render |
| `ComponentManager` | render |
| `ComponentModel` | render |
| `IComplexModel` | render |
| `ModelRamp` | render |
| `ModelSteepRamp` | render |
| `VehicleModels` | data |

## `com.mrcrayfish.vehicle.client.model.baked`

| Clasa | Stare |
|---|---|
| `BakedModelRamp` | render |
| `BakedModelSteepRamp` | render |

## `com.mrcrayfish.vehicle.client.particle`

| Clasa | Stare |
|---|---|
| `DustParticle` | todo |
| `TyreSmokeParticle` | todo |

## `com.mrcrayfish.vehicle.client.raytrace`

| Clasa | Stare |
|---|---|
| `EntityRayTracer` | todo |
| `ITriangleList` | todo |
| `InteractableBox` | todo |
| `InterceptResult` | todo |
| `MatrixTransform` | todo |
| `RayTraceFunction` | todo |
| `RayTraceTransforms` | todo |
| `TransformHelper` | todo |
| `Triangle` | todo |
| `TriangleList` | todo |
| `VehicleRayTraceResult` | todo |

## `com.mrcrayfish.vehicle.client.raytrace.data`

| Clasa | Stare |
|---|---|
| `BoxRayTraceData` | todo |
| `ComponentModelRayTraceData` | todo |
| `CosmeticRayTraceData` | todo |
| `InteractableBoxRayTraceData` | todo |
| `ItemStackRayTraceData` | todo |
| `RayTraceData` | todo |

## `com.mrcrayfish.vehicle.client.render`

| Clasa | Stare |
|---|---|
| `AbstractBoatRenderer` | render |
| `AbstractHelicopterRenderer` | render |
| `AbstractLandVehicleRenderer` | render |
| `AbstractMotorcycleRenderer` | render |
| `AbstractPlaneRenderer` | render |
| `AbstractPoweredRenderer` | render |
| `AbstractTrailerRenderer` | render |
| `AbstractVehicleRenderer` | render |
| `Axis` | render |
| `CachedVehicle` | render |
| `EntityVehicleRenderer` | render |
| `JackRenderer` | render |
| `VehicleRenderRegistry` | render |

## `com.mrcrayfish.vehicle.client.render.complex`

| Clasa | Stare |
|---|---|
| `ComplexModel` | render |

## `com.mrcrayfish.vehicle.client.render.complex.transforms`

| Clasa | Stare |
|---|---|
| `Rotate` | render |
| `Transform` | render |
| `Translate` | render |

## `com.mrcrayfish.vehicle.client.render.complex.value`

| Clasa | Stare |
|---|---|
| `Dynamic` | render |
| `IValue` | render |
| `Static` | render |

## `com.mrcrayfish.vehicle.client.render.layer`

| Clasa | Stare |
|---|---|
| `LayerHeldVehicle` | render |

## `com.mrcrayfish.vehicle.client.render.model`

| Clasa | Stare |
|---|---|
| `ChestModel` | render |

## `com.mrcrayfish.vehicle.client.render.tileentity`

| Clasa | Stare |
|---|---|
| `FluidExtractorRenderer` | render |
| `FluidPumpRenderer` | render |
| `FuelDrumRenderer` | render |
| `GasPumpRenderer` | render |
| `GasPumpTankRenderer` | render |
| `JackRenderer` | render |
| `VehicleCrateRenderer` | render |

## `com.mrcrayfish.vehicle.client.render.vehicle`

| Clasa | Stare |
|---|---|
| `CompactHelicopterRenderer` | render |
| `DirtBikeRenderer` | render |
| `FertilizerTrailerRenderer` | render |
| `FluidTrailerRenderer` | render |
| `GoKartRenderer` | render |
| `GolfCartRenderer` | render |
| `JetSkiRenderer` | render |
| `LawnMowerRenderer` | render |
| `MiniBusRenderer` | render |
| `MopedRenderer` | render |
| `OffRoaderRenderer` | render |
| `QuadBikeRenderer` | render |
| `SeederTrailerRenderer` | render |
| `SofaHelicopterRenderer` | render |
| `SportsCarRenderer` | render |
| `SportsPlaneRenderer` | render |
| `StorageTrailerRenderer` | render |
| `TractorRenderer` | render |
| `VehicleTrailerRenderer` | render |

## `com.mrcrayfish.vehicle.client.screen`

| Clasa | Stare |
|---|---|
| `CheckBox` | todo |
| `DashboardScreen` | todo |
| `EditVehicleScreen` | todo |
| `FluidExtractorScreen` | todo |
| `FluidMixerScreen` | todo |
| `StorageScreen` | todo |
| `WorkstationScreen` | todo |

## `com.mrcrayfish.vehicle.client.screen.toolbar`

| Clasa | Stare |
|---|---|
| `AbstractToolbarScreen` | todo |
| `IToolbarLabel` | todo |

## `com.mrcrayfish.vehicle.client.screen.toolbar.widget`

| Clasa | Stare |
|---|---|
| `DoorButton` | todo |
| `EnumButton` | todo |
| `IconButton` | todo |
| `ImprovedSlider` | todo |
| `NumberWidget` | todo |
| `SeatButton` | todo |
| `Spacer` | todo |

## `com.mrcrayfish.vehicle.client.util`

| Clasa | Stare |
|---|---|
| `HermiteInterpolator` | todo |
| `MathUtil` | todo |

## `com.mrcrayfish.vehicle.common`

| Clasa | Stare |
|---|---|
| `CommonEvents` | todo |
| `CosmeticTracker` | todo |
| `FluidNetworkHandler` | todo |
| `Seat` | todo |
| `SeatTracker` | todo |
| `SurfaceHelper` | todo |
| `VehicleRegistry` | todo |
| `VehicleTextComponents` | todo |

## `com.mrcrayfish.vehicle.common.command`

| Clasa | Stare |
|---|---|
| `SetCosmeticCommand` | todo |

## `com.mrcrayfish.vehicle.common.cosmetic`

| Clasa | Stare |
|---|---|
| `CosmeticActions` | todo |
| `CosmeticProperties` | todo |
| `StandardCosmetics` | todo |

## `com.mrcrayfish.vehicle.common.cosmetic.actions`

| Clasa | Stare |
|---|---|
| `Action` | todo |
| `OpenableAction` | todo |
| `RotateAction` | todo |

## `com.mrcrayfish.vehicle.common.data`

| Clasa | Stare |
|---|---|
| `Serializers` | todo |

## `com.mrcrayfish.vehicle.common.entity`

| Clasa | Stare |
|---|---|
| `HeldVehicleDataHandler` | todo |
| `Transform` | todo |

## `com.mrcrayfish.vehicle.common.inventory`

| Clasa | Stare |
|---|---|
| `IAttachableChest` | todo |
| `IStorage` | todo |
| `StorageInventory` | todo |

## `com.mrcrayfish.vehicle.common.slot`

| Clasa | Stare |
|---|---|
| `SlotStorage` | todo |

## `com.mrcrayfish.vehicle.crafting`

| Clasa | Stare |
|---|---|
| `FluidEntry` | todo |
| `FluidExtractorRecipe` | todo |
| `FluidExtractorRecipeSerializer` | todo |
| `FluidMixerRecipe` | todo |
| `FluidMixerRecipeSerializer` | todo |
| `RecipeType` | todo |
| `WorkstationIngredient` | todo |
| `WorkstationRecipe` | todo |
| `WorkstationRecipeSerializer` | todo |
| `WorkstationRecipes` | todo |

## `com.mrcrayfish.vehicle.datagen`

| Clasa | Stare |
|---|---|
| `FluidExtractorRecipeBuilder` | todo |
| `FluidMixerRecipeBuilder` | todo |
| `LootTableGen` | todo |
| `RecipeGen` | todo |
| `VehiclePropertiesGen` | todo |
| `VehiclePropertiesProvider` | todo |
| `WorkstationRecipeBuilder` | todo |

## `com.mrcrayfish.vehicle.entity`

| Clasa | Stare |
|---|---|
| `BoatEntity` | todo |
| `EngineTier` | todo |
| `EngineType` | todo |
| `EntityJack` | todo |
| `FuelFillerType` | todo |
| `HelicopterEntity` | todo |
| `IEngineTier` | todo |
| `IEngineType` | todo |
| `IWheelType` | todo |
| `LandVehicleEntity` | ported |
| `MotorcycleEntity` | todo |
| `PlaneEntity` | todo |
| `PoweredVehicleEntity` | partial |
| `TrailerEntity` | todo |
| `VehicleEntity` | partial |
| `Wheel` | todo |
| `WheelType` | todo |

## `com.mrcrayfish.vehicle.entity.properties`

| Clasa | Stare |
|---|---|
| `ExtendedProperties` | todo |
| `HelicopterProperties` | todo |
| `LandProperties` | todo |
| `MotorcycleProperties` | todo |
| `PlaneProperties` | todo |
| `PoweredProperties` | todo |
| `TrailerProperties` | todo |
| `VehicleProperties` | todo |

## `com.mrcrayfish.vehicle.entity.trailer`

| Clasa | Stare |
|---|---|
| `FertilizerTrailerEntity` | todo |
| `FluidTrailerEntity` | todo |
| `SeederTrailerEntity` | todo |
| `StorageTrailerEntity` | todo |
| `VehicleTrailerEntity` | todo |

## `com.mrcrayfish.vehicle.entity.vehicle`

| Clasa | Stare |
|---|---|
| `CompactHelicopterEntity` | todo |
| `DirtBikeEntity` | todo |
| `GoKartEntity` | todo |
| `GolfCartEntity` | todo |
| `JetSkiEntity` | todo |
| `LawnMowerEntity` | todo |
| `MiniBusEntity` | todo |
| `MopedEntity` | todo |
| `OffRoaderEntity` | todo |
| `QuadBikeEntity` | todo |
| `SofacopterEntity` | todo |
| `SportsCarEntity` | todo |
| `SportsPlaneEntity` | todo |
| `TractorEntity` | todo |

## `com.mrcrayfish.vehicle.fluid`

| Clasa | Stare |
|---|---|
| `BlazeJuice` | todo |
| `EnderSap` | todo |
| `Fuelium` | todo |

## `com.mrcrayfish.vehicle.init`

| Clasa | Stare |
|---|---|
| `ModBlocks` | todo |
| `ModCommands` | todo |
| `ModContainers` | todo |
| `ModDataKeys` | todo |
| `ModEntities` | ported |
| `ModFluids` | todo |
| `ModItems` | todo |
| `ModLootFunctions` | todo |
| `ModParticleTypes` | todo |
| `ModRecipeSerializers` | todo |
| `ModSounds` | todo |
| `ModTileEntities` | todo |

## `com.mrcrayfish.vehicle.inventory`

| Clasa | Stare |
|---|---|
| `IStorageBlock` | todo |

## `com.mrcrayfish.vehicle.inventory.container`

| Clasa | Stare |
|---|---|
| `EditVehicleContainer` | todo |
| `FluidExtractorContainer` | todo |
| `FluidMixerContainer` | todo |
| `StorageContainer` | todo |
| `WorkstationContainer` | todo |

## `com.mrcrayfish.vehicle.inventory.container.slot`

| Clasa | Stare |
|---|---|
| `FuelSlot` | todo |

## `com.mrcrayfish.vehicle.item`

| Clasa | Stare |
|---|---|
| `EngineItem` | todo |
| `FluidPipeItem` | todo |
| `HammerItem` | todo |
| `IDyeable` | todo |
| `ItemBoostRamp` | todo |
| `ItemTrafficCone` | todo |
| `JerryCanItem` | todo |
| `KeyItem` | todo |
| `PartItem` | todo |
| `SprayCanItem` | todo |
| `WheelItem` | todo |
| `WrenchItem` | todo |

## `com.mrcrayfish.vehicle.network`

| Clasa | Stare |
|---|---|
| `HandshakeHandler` | todo |
| `HandshakeMessages` | todo |
| `PacketHandler` | todo |

## `com.mrcrayfish.vehicle.network.datasync`

| Clasa | Stare |
|---|---|
| `VehicleDataValue` | todo |

## `com.mrcrayfish.vehicle.network.message`

| Clasa | Stare |
|---|---|
| `IMessage` | todo |
| `MessageAttachChest` | todo |
| `MessageAttachTrailer` | todo |
| `MessageCraftVehicle` | todo |
| `MessageCycleSeats` | todo |
| `MessageEntityFluid` | todo |
| `MessageFuelVehicle` | todo |
| `MessageHandbrake` | todo |
| `MessageHelicopterInput` | todo |
| `MessageHitchTrailer` | todo |
| `MessageHorn` | todo |
| `MessageInteractCosmetic` | todo |
| `MessageInteractKey` | todo |
| `MessageOpenStorage` | todo |
| `MessagePickupVehicle` | todo |
| `MessagePlaneInput` | todo |
| `MessageSetSeat` | todo |
| `MessageSyncActionData` | todo |
| `MessageSyncCosmetics` | todo |
| `MessageSyncHeldVehicle` | todo |
| `MessageSyncPlayerSeat` | todo |
| `MessageSyncStorage` | todo |
| `MessageThrottle` | todo |
| `MessageThrowVehicle` | todo |
| `MessageTurnAngle` | todo |

## `com.mrcrayfish.vehicle.network.play`

| Clasa | Stare |
|---|---|
| `ClientPlayHandler` | todo |
| `ServerPlayHandler` | todo |

## `com.mrcrayfish.vehicle.recipe`

| Clasa | Stare |
|---|---|
| `RecipeColorSprayCan` | todo |
| `RecipeRefillSprayCan` | todo |

## `com.mrcrayfish.vehicle.tileentity`

| Clasa | Stare |
|---|---|
| `BoostTileEntity` | todo |
| `FluidExtractorTileEntity` | todo |
| `FluidMixerTileEntity` | todo |
| `FuelDrumTileEntity` | todo |
| `GasPumpTankTileEntity` | todo |
| `GasPumpTileEntity` | todo |
| `IFluidTankWriter` | todo |
| `IndustrialFuelDrumTileEntity` | todo |
| `JackTileEntity` | todo |
| `PipeTileEntity` | todo |
| `PumpTileEntity` | todo |
| `TileEntitySynced` | todo |
| `TileFluidHandlerSynced` | todo |
| `VehicleCrateTileEntity` | todo |
| `WorkstationTileEntity` | todo |

## `com.mrcrayfish.vehicle.util`

| Clasa | Stare |
|---|---|
| `Axis` | todo |
| `Bounds` | todo |
| `CollisionHelper` | todo |
| `CommonUtils` | ported |
| `EasingHelper` | todo |
| `ExtraJSONUtils` | todo |
| `FluidUtils` | todo |
| `InventoryUtil` | todo |
| `LazyValueFunction` | todo |
| `RenderUtil` | todo |
| `StateHelper` | todo |
| `TileEntityUtil` | todo |
| `VehicleUtil` | todo |
| `VoxelShapeHelper` | todo |

## `com.mrcrayfish.vehicle.world.storage.loot.functions`

| Clasa | Stare |
|---|---|
| `CopyFluidTanks` | data |
