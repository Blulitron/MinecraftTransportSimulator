package mcinterface1122;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.lwjgl.opengl.GL11;

import minecrafttransportsimulator.baseclasses.BoundingBox;
import minecrafttransportsimulator.baseclasses.FluidTank;
import minecrafttransportsimulator.baseclasses.Point3d;
import minecrafttransportsimulator.baseclasses.Point3i;
import minecrafttransportsimulator.guis.components.AGUIBase.TextPosition;
import minecrafttransportsimulator.guis.instances.GUIHUD;
import minecrafttransportsimulator.items.components.AItemBase;
import minecrafttransportsimulator.items.components.AItemPack;
import minecrafttransportsimulator.jsondefs.JSONText;
import minecrafttransportsimulator.jsondefs.JSONVehicle.VehicleAnimationDefinition;
import minecrafttransportsimulator.jsondefs.JSONVehicle.VehicleCameraObject;
import minecrafttransportsimulator.mcinterface.IInterfaceRender;
import minecrafttransportsimulator.mcinterface.IWrapperEntity;
import minecrafttransportsimulator.packloading.PackResourceLoader;
import minecrafttransportsimulator.packloading.PackResourceLoader.ResourceType;
import minecrafttransportsimulator.rendering.components.AParticle;
import minecrafttransportsimulator.rendering.components.RenderTickData;
import minecrafttransportsimulator.systems.ConfigSystem;
import minecrafttransportsimulator.systems.PackParserSystem;
import minecrafttransportsimulator.systems.VehicleAnimationSystem;
import minecrafttransportsimulator.vehicles.main.AEntityBase;
import minecrafttransportsimulator.vehicles.main.EntityVehicleF_Physics;
import minecrafttransportsimulator.vehicles.parts.APart;
import minecrafttransportsimulator.vehicles.parts.PartInteractable;
import minecrafttransportsimulator.vehicles.parts.PartSeat;
import net.minecraft.block.SoundType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.MetadataSerializer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.client.event.EntityViewRenderEvent.CameraSetup;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.IRenderFactory;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(Side.CLIENT)
class InterfaceRender implements IInterfaceRender{
	private static final Map<String, Integer> textures = new HashMap<String, Integer>();
	private static final Map<BuilderEntity, RenderTickData> renderData = new HashMap<BuilderEntity, RenderTickData>();
	private static String pushedTextureLocation;
	private static int zoomLevel;
	private static int customCameraIndex;
	private static boolean runningCustomCameras;
	
	@Override
	public int getRenderPass(){
		return MinecraftForgeClient.getRenderPass();
	}
	
	@Override
	public boolean shouldRenderBoundingBoxes(){
		return Minecraft.getMinecraft().getRenderManager().isDebugBoundingBox() && getRenderPass() != 1;
	}
	
	@Override
	public void bindTexture(String textureLocation){
		//If the texture has a colon, it's a short-hand form that needs to be converted.
		if(textureLocation.indexOf(":") != -1){
			textureLocation = "/assets/" + textureLocation.replace(":", "/");
		}
		//Bind texture if we have it.
		if(!textures.containsKey(textureLocation)){
			//Don't have this texture created yet.  Do so now.
			//Parse the texture, get the OpenGL integer that represents this texture, and save it.
			//FAR less jank than using MC's resource system.
			try{
				BufferedImage bufferedimage = TextureUtil.readBufferedImage(InterfaceRender.class.getResourceAsStream(textureLocation));
				int glTexturePointer = TextureUtil.glGenTextures();
		        TextureUtil.uploadTextureImageAllocate(glTexturePointer, bufferedimage, false, false);
		        textures.put(textureLocation, glTexturePointer);
			}catch(Exception e){
				MasterInterface.coreInterface.logError("ERROR: Could not find texture: " + textureLocation + " Reverting to fallback texture.");
				textures.put(textureLocation, TextureUtil.MISSING_TEXTURE.getGlTextureId());
			}
		}
		GlStateManager.bindTexture(textures.get(textureLocation));
	}
	
	@Override
	public void setTexture(String textureLocation){
		pushedTextureLocation = textureLocation;
		bindTexture(textureLocation);
	}
	
	@Override
	public void recallTexture(){
		if(pushedTextureLocation != null){
			GlStateManager.bindTexture(textures.get(pushedTextureLocation));
		}
	}
	
	@Override
	public void setLightingState(boolean enabled){
		setSystemLightingState(enabled);
		setInternalLightingState(enabled);
	}
	
	@Override
	public void setSystemLightingState(boolean enabled){
		if(enabled){
			GlStateManager.enableLighting();
		}else{
			GlStateManager.disableLighting();
		}
	}
	
	@Override
	public void setInternalLightingState(boolean enabled){
		if(enabled){
			Minecraft.getMinecraft().entityRenderer.enableLightmap();
		}else{
			Minecraft.getMinecraft().entityRenderer.disableLightmap();
		}
	}
	
	@Override
	public void setLightingToEntity(AEntityBase entity){
		if(getRenderPass() == -1){
	        RenderHelper.enableStandardItemLighting();
	        setLightingState(true);
        }
		int lightVar = BuilderEntity.createdClientBuilders.get(entity).getBrightnessForRender();
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightVar%65536, lightVar/65536);
	}
	
	@Override
	public void setLightingToBlock(Point3i location){
		if(getRenderPass() == -1){
	        RenderHelper.enableStandardItemLighting();
	        setLightingState(true);
        }
		int lightVar = Minecraft.getMinecraft().world.getCombinedLight(new BlockPos(location.x, location.y, location.z), 0);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightVar%65536, lightVar/65536);
	}
	
	@Override
	public void setBlendState(boolean enabled, boolean brightBlend){
		if(enabled){
			GlStateManager.enableBlend();
			GlStateManager.disableAlpha();
			GlStateManager.depthMask(false);
			if(brightBlend){
				GlStateManager.blendFunc(GL11.GL_DST_COLOR, GL11.GL_SRC_ALPHA);
			}else{
				GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
			}
		}else{
			GlStateManager.disableBlend();
			GlStateManager.enableAlpha();
			GlStateManager.depthMask(true);
			GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
		}
	}
	
	@Override
	public void setColorState(float red, float green, float blue, float alpha){
		GlStateManager.color(red, green, blue, alpha);
	}
	
	@Override
	public void resetStates(){
		//For pass 0, we do lighting but not blending.
		//For pass 1, we do blending and lighting.
		//For pass -1, we don't do blending or lighting.
		setColorState(1.0F, 1.0F, 1.0F, 1.0F);
		setBlendState(getRenderPass() == 1, false);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		if(getRenderPass() != -1){
			setLightingState(true);
		}else{
			setLightingState(false);
			RenderHelper.disableStandardItemLighting();
		}
	}
	
	@Override
	public void changeCameraZoom(boolean zoomIn){
		if(zoomIn && zoomLevel > 0){
			zoomLevel -= 2;
		}else if(!zoomIn){
			zoomLevel += 2;
		}
	}
	
	@Override
	public void renderEntityRiders(AEntityBase entity, float partialTicks){
		for(IWrapperEntity rider : entity.locationRiderMap.values()){
			Entity riderEntity = ((WrapperEntity) rider).entity;
			if(!(MasterInterface.gameInterface.getClientPlayer().equals(rider) && MasterInterface.gameInterface.inFirstPerson()) && riderEntity.posY > riderEntity.world.getHeight()){
				GL11.glPushMatrix();
				Point3d riderPosition = rider.getRenderedPosition(partialTicks);
				GL11.glTranslated(riderPosition.x, riderPosition.y, riderPosition.z);
				Minecraft.getMinecraft().getRenderManager().renderEntityStatic(riderEntity, partialTicks, false);
				GL11.glPopMatrix();
			}
		}
	}
	
	@Override
	public void spawnParticle(AParticle particle){
		if(Minecraft.getMinecraft().effectRenderer != null){
			Minecraft.getMinecraft().effectRenderer.addEffect(new BuilderParticle(particle));
		}
	}
	
	@Override
	public void spawnBlockBreakParticles(Point3i point){
		if(Minecraft.getMinecraft().effectRenderer != null){
			BlockPos pos = new BlockPos(point.x, point.y, point.z);
			SoundType soundType = Minecraft.getMinecraft().world.getBlockState(pos).getBlock().getSoundType(Minecraft.getMinecraft().world.getBlockState(pos), Minecraft.getMinecraft().player.world, pos, null);
			Minecraft.getMinecraft().world.playSound(null, pos, soundType.getBreakSound(), SoundCategory.BLOCKS, soundType.getVolume(), soundType.getPitch());
			Minecraft.getMinecraft().effectRenderer.addBlockHitEffects(pos, EnumFacing.UP);
		}
	}
	
	@Override
	public boolean renderTextMarkings(List<JSONText> textDefinitions, List<String> textLines, String objectRendering, boolean lightsOn){
		if(getRenderPass() != 1){
			boolean systemLightingEnabled = true;
			boolean internalLightingEnabled = true;
			if(textDefinitions != null){
				for(byte i=0; i<textDefinitions.size(); ++i){
					JSONText textDefinition = textDefinitions.get(i);
					String text = textLines.get(i);
					
					//Render if our attached object and the object we are rendering on match.
					if(textDefinition.attachedTo == null ? objectRendering == null : textDefinition.attachedTo.equals(objectRendering)){
						//Disable system lighting if we haven't already.
						//System lighting doesn't work well with text.
						if(systemLightingEnabled){
							setSystemLightingState(false);
							systemLightingEnabled = false;
						}
						
						//If we have light-up text, disable lightmap.
						if(textDefinition.lightsUp && lightsOn){
							if(internalLightingEnabled){
								internalLightingEnabled = false;
								setInternalLightingState(internalLightingEnabled);
							}
						}else if(!internalLightingEnabled){
							internalLightingEnabled = true;
							setInternalLightingState(internalLightingEnabled);
						}
						//System.out.println(text);
						GL11.glPushMatrix();
						//Translate to the position to render.
						GL11.glTranslated(textDefinition.pos.x, textDefinition.pos.y, textDefinition.pos.z);
						//First rotate 180 along the X-axis to get us rendering right-side up.
						GL11.glRotatef(180F, 1, 0, 0);
						//Next, apply rotations.  Y is inverted due to the inverted X axis.
						GL11.glRotated(-textDefinition.rot.y, 0, 1, 0);
						GL11.glRotated(textDefinition.rot.x, 1, 0, 0);
						GL11.glRotated(textDefinition.rot.z, 0, 0, 1);
						//Scale by 1/16.  This converts us from block units to pixel units, which is what the GUIs use.
						GL11.glScalef(1F/16F, 1F/16F, 1F/16F);
						//Finally, render the text.
						MasterInterface.guiInterface.drawScaledText(text, 0, 0, Color.decode(textDefinition.color), TextPosition.values()[textDefinition.renderPosition], textDefinition.wrapWidth, textDefinition.scale, textDefinition.autoScale);
						GL11.glPopMatrix();
					}
				}
			}
			
			//Reset lighting.
			if(!internalLightingEnabled){
				setInternalLightingState(true);
			}
			if(!systemLightingEnabled){
				setSystemLightingState(true);
				//Set color back to white, the font renderer sets this to not-white.
				setColorState(1.0F, 1.0F, 1.0F, 1.0F);
				return true;
			}else{
				return false;
			}
		}else{
			return false;
		}
	}
	
	
	
	
	//--------------------START OF EVENT METHODS--------------------
	 /**
     * Pre-post methods for adjusting player angles while seated.
     */
    @SubscribeEvent
    public static void on(RenderPlayerEvent.Pre event){
    	EntityPlayer renderedPlayer = event.getEntityPlayer();
    	if(renderedPlayer.getRidingEntity() instanceof BuilderEntity){
        	AEntityBase ridingEntity = ((BuilderEntity) renderedPlayer.getRidingEntity()).entity;
        	GL11.glPushMatrix();
        	if(ridingEntity != null){
        		//Get total angles for the entity the player is riding.
        		Point3d totalAngles = ridingEntity.angles.copy();
	            if(ridingEntity instanceof EntityVehicleF_Physics){
	            	for(IWrapperEntity rider : ridingEntity.locationRiderMap.values()){
						if(Minecraft.getMinecraft().player.equals(((WrapperEntity) rider).entity)){
							PartSeat seat = (PartSeat) ((EntityVehicleF_Physics) ridingEntity).getPartAtLocation(ridingEntity.locationRiderMap.inverse().get(rider));
		            		totalAngles = ridingEntity.angles.copy().add(seat.placementRotation).add(seat.getPositionRotation(event.getPartialRenderTick()).add(seat.getActionRotation(event.getPartialRenderTick())));
		            		if(seat.parentPart != null){
		            			totalAngles.add(seat.parentPart.placementRotation).add(seat.parentPart.getPositionRotation(event.getPartialRenderTick()).add(seat.parentPart.getActionRotation(event.getPartialRenderTick())));
			            	}
						}
	            	}
	            }
	            
        		//Set the player yaw offset to 0.  This is needed as we are rotating the player manually.
	            renderedPlayer.renderYawOffset = 0;
	            
	            //Set the player's head yaw to the delta between their yaw and their angled yaw.
	            renderedPlayer.rotationYawHead = (float) (renderedPlayer.rotationYaw + totalAngles.y); 
	            
	            //Now add the rotations.
	            //We have to do this via OpenGL, as changing the player's pitch doesn't make them tilt in the seat, and roll doesn't exist for them.
	            //In this case, the player's eyes are their center point for rotation, but these aren't the same as 
	            //their actual position.  Means we have to do funky math.
	            //We also need to check if we are the client player or another player, as other players require a
	            //different pre-render offset to be performed to get them into the right place. 
	            if(!renderedPlayer.equals(Minecraft.getMinecraft().player)){
	            	EntityPlayerSP masterPlayer = Minecraft.getMinecraft().player;
	            	double playerDistanceX = renderedPlayer.lastTickPosX + - masterPlayer.lastTickPosX + (renderedPlayer.posX - renderedPlayer.lastTickPosX -(masterPlayer.posX - masterPlayer.lastTickPosX))*event.getPartialRenderTick();
	            	double playerDistanceY = renderedPlayer.lastTickPosY + - masterPlayer.lastTickPosY + (renderedPlayer.posY - renderedPlayer.lastTickPosY -(masterPlayer.posY - masterPlayer.lastTickPosY))*event.getPartialRenderTick();
	            	double playerDistanceZ = renderedPlayer.lastTickPosZ + - masterPlayer.lastTickPosZ + (renderedPlayer.posZ - renderedPlayer.lastTickPosZ -(masterPlayer.posZ - masterPlayer.lastTickPosZ))*event.getPartialRenderTick();
	                GL11.glTranslated(playerDistanceX, playerDistanceY, playerDistanceZ);
	                
	                GL11.glTranslated(0, renderedPlayer.getEyeHeight(), 0);
	                GL11.glRotated(totalAngles.y, 0, 1, 0);
	                GL11.glRotated(totalAngles.x, 1, 0, 0);
	                GL11.glRotated(totalAngles.z, 0, 0, 1);
	                GL11.glTranslated(0, -renderedPlayer.getEyeHeight(), 0);
	                
	                GL11.glTranslated(-playerDistanceX, -playerDistanceY, -playerDistanceZ);
	            }else{
	            	GL11.glTranslated(0, renderedPlayer.getEyeHeight(), 0);
	            	GL11.glRotated(totalAngles.y, 0, 1, 0);
	            	GL11.glRotated(totalAngles.x, 1, 0, 0);
	            	GL11.glRotated(totalAngles.z, 0, 0, 1);
	            	GL11.glTranslated(0, -renderedPlayer.getEyeHeight(), 0);
	            }
        	}
        }
    }

    @SubscribeEvent
    public static void on(RenderPlayerEvent.Post event){
    	if(event.getEntityPlayer().getRidingEntity() instanceof BuilderEntity){
    		GL11.glPopMatrix();
        }
    }
    
    /**
     * Adjusts roll, pitch, and zoom for camera.
     * Roll and pitch only gets updated when in first-person as we use OpenGL transforms.
     * For external rotations, we just let the entity adjust the player's pitch and yaw.
     * This is because first-person view is for direct control, while third-person is for passive control.
     */
    @SubscribeEvent
    public static void on(CameraSetup event){
    	Entity player = event.getEntity();
    	if(player.getRidingEntity() instanceof BuilderEntity){
    		AEntityBase entity = ((BuilderEntity) player.getRidingEntity()).entity;
    		if(entity instanceof EntityVehicleF_Physics){
    			EntityVehicleF_Physics vehicle = (EntityVehicleF_Physics) entity;
    			Point3d riderLocation = vehicle.locationRiderMap.inverse().get(WrapperWorld.getWrapperFor(event.getEntity().world).getWrapperFor(event.getEntity()));
        		if(riderLocation != null){
		    		if(MasterInterface.gameInterface.inFirstPerson()){
		    			//Do custom camera, or do normal rendering.
		    			if(runningCustomCameras){
		    				//Check to make sure we didn't switch vehicles and foul up our cameras.
		    				if(vehicle.definition.rendering.cameraObjects != null && customCameraIndex < vehicle.definition.rendering.cameraObjects.size()){
			    				//Get the camera to render.
		    					VehicleCameraObject camera = vehicle.definition.rendering.cameraObjects.get(customCameraIndex);
		    					
		    					//Remove MC rotations before doing any of our own.
		    					event.setPitch(0);
		            			event.setYaw(0);
		            			
		            			//First rotate by 180 to get the forwards-facing orientation; MC does everything backwards.
		                		GL11.glRotated(180, 0, 1, 0);
		                		
		                		//Rotate to the camera's rotation, if it has one.
		            			if(camera.rot != null){
		            	    		GL11.glRotated(-camera.rot.y, 0, 1, 0);
		            	    		GL11.glRotated(-camera.rot.x, 1, 0, 0);
		            	    		GL11.glRotated(-camera.rot.z, 0, 0, 1);
		            			}
		            			
		            			//Apply any rotations from rotation animations.
		            			if(camera.animations != null){
		            				for(VehicleAnimationDefinition animation : camera.animations){
		            					double animationValue = VehicleAnimationSystem.getVariableValue(animation.variable, animation.axis.length(), animation.offset, animation.clampMin, animation.clampMax, animation.absolute, (float) event.getRenderPartialTicks(), vehicle, null);
		            					if(animation.animationType.equals("rotation")){
		            						if(animationValue != 0){
		            							Point3d rotationAxis = animation.axis.copy().normalize();
		                						if(animationValue != 0){
		                							GL11.glTranslated(animation.centerPoint.x - camera.pos.x, animation.centerPoint.y - camera.pos.y, animation.centerPoint.z - camera.pos.z);
		                							GL11.glRotated(animationValue, -rotationAxis.x, -rotationAxis.y, -rotationAxis.z);
		                							GL11.glTranslated(-(animation.centerPoint.x - camera.pos.x), -(animation.centerPoint.y - camera.pos.y), -(animation.centerPoint.z - camera.pos.z));
		                						}
		            						}
		            					}
		            				}
		            			}
		                		
		                		//Translate to the camera's position.
		            			//Need to take into account the player's eye height.  This is where the camera is, but not where the player is positioned.
		            			double playerPositionToEyeOffset = 0.87;
		            			GL11.glTranslated(-(camera.pos.x - riderLocation.x), -(camera.pos.y - playerPositionToEyeOffset - riderLocation.y), -(camera.pos.z - riderLocation.z));
		            			
		            			//Translate again to any camera animations.
		            			if(camera.animations != null){
		            				for(VehicleAnimationDefinition animation : camera.animations){
		            					double animationValue = VehicleAnimationSystem.getVariableValue(animation.variable, animation.axis.length(), animation.offset, animation.clampMin, animation.clampMax, animation.absolute, (float) event.getRenderPartialTicks(), vehicle, null);
		            					if(animation.animationType.equals("translation")){
		            						if(animationValue != 0){
		            							if(animation.animationType.equals("translation")){
		                    						Point3d translationAmount = animation.axis.copy().normalize().multiply(animationValue);
		                    						GL11.glTranslated(-translationAmount.x, -translationAmount.y, -translationAmount.z);
		                    					}
		            						}
		            					}
		            				}
		            			}
		                		
		            			//Now rotate to match the vehicle's angles.
		            			Point3d vehicleSmoothedRotation = vehicle.prevAngles.copy().add(vehicle.angles.copy().subtract(vehicle.prevAngles).multiply(event.getRenderPartialTicks()));
		                		GL11.glRotated(-vehicleSmoothedRotation.x, 1, 0, 0);
		            			GL11.glRotated(-vehicleSmoothedRotation.y, 0, 1, 0);
		            			GL11.glRotated(-vehicleSmoothedRotation.z, 0, 0, 1);
		    				}else{
		    					runningCustomCameras = false;
		    				}
		    			}else{
			            	//Get yaw delta between entity and player from-180 to 180.
			            	double playerYawDelta = (360 + (vehicle.angles.y - -event.getEntity().rotationYaw)%360)%360;
			            	if(playerYawDelta > 180){
			            		playerYawDelta-=360;
			            	}
			            	
			            	//Get the angles from -180 to 180 for use by the component system for calculating roll and pitch angles.
			            	double pitchAngle = vehicle.prevAngles.x + (vehicle.angles.x - vehicle.prevAngles.x)*event.getRenderPartialTicks();
			            	double rollAngle = vehicle.prevAngles.z + (vehicle.angles.z - vehicle.prevAngles.z)*event.getRenderPartialTicks();
			            	while(pitchAngle > 180){pitchAngle -= 360;}
			    			while(pitchAngle < -180){pitchAngle += 360;}
			    			while(rollAngle > 180){rollAngle -= 360;}
			    			while(rollAngle < -180){rollAngle += 360;}
			            	
			            	//Get the component of the pitch and roll that should be applied based on the yaw delta.
			            	//This is based on where the player is looking.  If the player is looking straight forwards, then we want 100% of the
			            	//pitch to be applied as pitch.  But, if they are looking to the side, then we need to apply that as roll, not pitch.
			            	double rollRollComponent = Math.cos(Math.toRadians(playerYawDelta))*rollAngle;
			            	double pitchRollComponent = -Math.sin(Math.toRadians(playerYawDelta))*pitchAngle;
			            	GL11.glRotated(rollRollComponent + pitchRollComponent, 0, 0, 1);
		    			}
		        	}else if(MasterInterface.gameInterface.inThirdPerson()){
		        		//If we were running a custom camera, and hit the switch key, increment our camera index.
		        		//We then go back to first-person to render the proper camera.
		        		if(runningCustomCameras){
		        			if(vehicle.definition.rendering.cameraObjects != null && customCameraIndex < vehicle.definition.rendering.cameraObjects.size() - 1){
		        				++customCameraIndex;
		        			}else{
		        				runningCustomCameras = false;
		        			}
		        			Minecraft.getMinecraft().gameSettings.thirdPersonView = 0;
		        		}
		        		GL11.glTranslated(-riderLocation.x, 0F, -zoomLevel);
		            }else{
		            	//Got to inverted third-person.  Switch to custom cameras if we have any.
		            	//If we do, go back to first-person to render the custom camera.
		            	if(vehicle.definition.rendering.cameraObjects != null){
		            		runningCustomCameras = true;
		            		customCameraIndex = 0;
		            		Minecraft.getMinecraft().gameSettings.thirdPersonView = 0;
		            	}else{
		            		GL11.glTranslatef(0, 0F, zoomLevel);
		            	}
		            }
        		}
    		}
        }
    }

    private static BuilderGUI currentHUD = null;
    /**
     * Renders the HUD on vehicles, or the fluid in a tank if we are mousing-over a vehicle.
     */
    @SubscribeEvent
    public static void on(RenderGameOverlayEvent.Post event){
    	if(event.getType().equals(RenderGameOverlayEvent.ElementType.HOTBAR)){
    		if(MasterInterface.gameInterface.inFirstPerson() && Minecraft.getMinecraft().player.getRidingEntity() == null){
    			RayTraceResult lastHit = Minecraft.getMinecraft().objectMouseOver;
    			if(lastHit.entityHit instanceof BuilderEntity){
    				BuilderEntity builder = (BuilderEntity) Minecraft.getMinecraft().objectMouseOver.entityHit;
    				if(builder.entity instanceof EntityVehicleF_Physics){
    					EntityVehicleF_Physics vehicle = (EntityVehicleF_Physics) builder.entity;
    					for(BoundingBox box : vehicle.interactionBoxes){
    						if(box.isPointInside(new Point3d(lastHit.hitVec.x, lastHit.hitVec.y, lastHit.hitVec.z))){
    							APart part = vehicle.getPartAtLocation(box.localCenter);
    							if(part instanceof PartInteractable){
    								FluidTank tank = ((PartInteractable) part).tank;
    								if(tank != null){
    									String tankText = tank.getFluid().isEmpty() ? "EMPTY" : tank.getFluid().toUpperCase() + " : " + tank.getFluidLevel() + "/" + tank.getMaxLevel();
    									MasterInterface.guiInterface.drawBasicText(tankText, event.getResolution().getScaledWidth()/2 + 4, event.getResolution().getScaledHeight()/2, Color.WHITE, TextPosition.LEFT_ALIGNED, 0);
    									return;
    								}
    							}
    						}
    					}
    				}
    			}
    		}else if(MasterInterface.gameInterface.inFirstPerson() ? ConfigSystem.configObject.client.renderHUD_1P.value : ConfigSystem.configObject.client.renderHUD_3P.value){
				if(Minecraft.getMinecraft().player.getRidingEntity() instanceof BuilderEntity){
					AEntityBase ridingEntity = ((BuilderEntity) Minecraft.getMinecraft().player.getRidingEntity()).entity;
					if(ridingEntity instanceof EntityVehicleF_Physics){
						for(IWrapperEntity rider : ridingEntity.locationRiderMap.values()){
							if(MasterInterface.gameInterface.getClientPlayer().equals(rider)){
								PartSeat seat = (PartSeat) ((EntityVehicleF_Physics) ridingEntity).getPartAtLocation(ridingEntity.locationRiderMap.inverse().get(rider));
								if(seat.vehicleDefinition.isController){
									//Make a new HUD if we need to.
									if(currentHUD == null){
										currentHUD = new BuilderGUI(new GUIHUD((EntityVehicleF_Physics) ridingEntity));
										currentHUD.initGui();
										currentHUD.setWorldAndResolution(Minecraft.getMinecraft(), event.getResolution().getScaledWidth(), event.getResolution().getScaledHeight());
									}
									
									//Translate far enough to not render behind the items.
									//Also translate down if we are a helf-HUD.
									GL11.glPushMatrix();
			                		GL11.glTranslated(0, 0, 250);
			                		if(MasterInterface.gameInterface.inFirstPerson() ? !ConfigSystem.configObject.client.fullHUD_1P.value : !ConfigSystem.configObject.client.fullHUD_3P.value){
			                			GL11.glTranslated(0, currentHUD.gui.getHeight()/2D, 0);
			                		}
			                		
			                		//Enable alpha testing.
			                		GL11.glEnable(GL11.GL_ALPHA_TEST);
			                		
			                		//Draw the HUD.
			                		currentHUD.drawScreen(0, 0, event.getPartialTicks());
			                		
			                		//Disable the translating, lightmap, alpha to put it back to its old state.
			                		GL11.glPopMatrix();
			                		MasterInterface.renderInterface.setInternalLightingState(false);
			                		GL11.glDisable(GL11.GL_ALPHA_TEST);
			                		
			                		//Return to prevent saved HUD from being wiped.
			                		return;
								}
							}
						}
					}
				}
			}
    	}
    	
    	//No HUD rendered, set it to null.
    	currentHUD = null;
    }
	
	/**
     * Used to force rendering of entities above the world height limit, as
     * newer versions suppress this as part of the chunk visibility
     * feature.
     */
    @SubscribeEvent
    public static void on(RenderWorldLastEvent event){
    	Minecraft.getMinecraft().world.profiler.startSection("iv_render_pass_-1");
        for(Entity entity : Minecraft.getMinecraft().world.loadedEntityList){
            if(entity instanceof BuilderEntity){
            	Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(entity).doRender(entity, 0, 0, 0, 0, event.getPartialTicks());
            }
        }
        Entity renderViewEntity = Minecraft.getMinecraft().getRenderViewEntity();
		double playerX = renderViewEntity.lastTickPosX + (renderViewEntity.posX - renderViewEntity.lastTickPosX) * event.getPartialTicks();
		double playerY = renderViewEntity.lastTickPosY + (renderViewEntity.posY - renderViewEntity.lastTickPosY) * event.getPartialTicks();
		double playerZ = renderViewEntity.lastTickPosZ + (renderViewEntity.posZ - renderViewEntity.lastTickPosZ) * event.getPartialTicks();
        for(TileEntity tile : Minecraft.getMinecraft().world.loadedTileEntityList){
        	if(tile instanceof BuilderTileEntity){
        		Vec3d delta = new Vec3d(tile.getPos()).addVector(-playerX, -playerY, -playerZ);
        		//Prevent crashing on corrupted TEs.
        		if(TileEntityRendererDispatcher.instance.getRenderer(tile) != null){
        			TileEntityRendererDispatcher.instance.getRenderer(tile).render(tile, delta.x, delta.y, delta.z, event.getPartialTicks(), 0, 0);
        		}
        	}
        }
        Minecraft.getMinecraft().world.profiler.endSection();
    }
    
	/**
	 *  Event that's called to register models.  We register our render wrapper
	 *  classes here, as well as all item JSONs.
	 */
	@SubscribeEvent
	public static void registerModels(ModelRegistryEvent event){
		//Create the custom JSON parser class.
		//We need to register a custom resource handler here to auto-generate JSON.
		//FAR easier than trying to use the bloody bakery system.
		((SimpleReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).reloadResourcePack(new PackResourcePack(MasterInterface.MODID + "_packs"));
		
		//Register the vehicle rendering class.
		RenderingRegistry.registerEntityRenderingHandler(BuilderEntity.class, new IRenderFactory<BuilderEntity>(){
			@Override
			public Render<? super BuilderEntity> createRenderFor(RenderManager manager){
			return new Render<BuilderEntity>(manager){
				@Override
				protected ResourceLocation getEntityTexture(BuilderEntity builder){
					return null;
				}
				
				@Override
				public void doRender(BuilderEntity builder, double x, double y, double z, float entityYaw, float partialTicks){
					if(builder.entity != null){
						Minecraft.getMinecraft().world.profiler.startSection("iv_render_entity_" + builder.entity.lookupID);
						//If we don't have render data yet, create one now.
						if(!renderData.containsKey(builder)){
							renderData.put(builder, new RenderTickData(builder.entity.world));
						}
						
						//Get render pass.  Render data uses 2 for pass -1 as it uses arrays and arrays can't have a -1 index.
						int renderPass = MasterInterface.renderInterface.getRenderPass();
						if(renderPass == -1){
							renderPass = 2;
						}
						
						//If we need to render, do so now.
						if(renderData.get(builder).shouldRender(renderPass, partialTicks)){
							builder.entity.render(partialTicks);
						}
						Minecraft.getMinecraft().world.profiler.endSection();
					}
				}
			};
		}});
		
		//Register the TESR wrapper.
		ClientRegistry.bindTileEntitySpecialRenderer(BuilderTileEntity.class, new BuilderTileEntityRender());
		
		//Register the item models.
		//First register the core items.
		for(Entry<AItemBase, BuilderItem> entry : BuilderItem.itemWrapperMap.entrySet()){
			try{
				//TODO remove this when we don't have non-pack items.
				if(!(entry.getValue().item instanceof AItemPack)){
					registerCoreItemRender(entry.getValue());
				}
			}catch(Exception e){
				e.printStackTrace();
			}
		}
		
		//Now register items for the packs.
		//If we ever register a pack item from a non-external pack, we'll need to make a resource loader for it.
		//This is done to allow MC/Forge to play nice with item textures.
		for(AItemPack<?> packItem : PackParserSystem.getAllPackItems()){
			//TODO remove this when the internal system actually works.
			if(PackParserSystem.getPackConfiguration(packItem.definition.packID) == null || PackParserSystem.getPackConfiguration(packItem.definition.packID).internallyGenerated){
				ModelLoader.setCustomModelResourceLocation(BuilderItem.itemWrapperMap.get(packItem), 0, new ModelResourceLocation(MasterInterface.MODID + "_packs:" + packItem.definition.packID + "." + packItem.getRegistrationName(), "inventory"));
			}else{
				if(!PackResourcePack.createdLoaders.containsKey(packItem.definition.packID)){
					((SimpleReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).reloadResourcePack(new PackResourcePack(packItem.definition.packID));
				}
				ModelLoader.setCustomModelResourceLocation(BuilderItem.itemWrapperMap.get(packItem), 0, new ModelResourceLocation(MasterInterface.MODID + "_packs:" + packItem.getRegistrationName(), "inventory"));
			}
		}
	}
	
	/**
	 *  Helper method to register renders.
	 */
	private static void registerCoreItemRender(Item item){
		ModelLoader.setCustomModelResourceLocation(item, 0, new ModelResourceLocation(MasterInterface.MODID + ":" + item.getRegistryName().getResourcePath(), "inventory"));
	}
	
	/**
	 *  Custom ResourcePack class for auto-generating item JSONs.
	 */
	private static class PackResourcePack implements IResourcePack{
	    private static final Map<String, PackResourcePack> createdLoaders = new HashMap<String, PackResourcePack>();
		private final String packDomain;
	    private final Set<String> domains;
		
		private PackResourcePack(String packDomain){
			this.packDomain = packDomain;
			domains = new HashSet<String>();
			domains.add(packDomain);
			createdLoaders.put(packDomain, this);
		}

		@Override
		public InputStream getInputStream(ResourceLocation location) throws IOException{
			String rawPackInfo = location.getResourcePath();
			try{
				//Get the resource type.
				boolean itemJSON = rawPackInfo.endsWith(".json");
				
				//If we are for an item JSON, try to find that JSON, or generate one automatically.
				//If we are for an item PNG, just load the PNG as-is.  If we don't find it, then just let MC purple checker it.
				//Note that the internal mts_packs loader does not do PNG loading, as it re-directs the PNG files to the pack's loaders.
				if(itemJSON){
					//Strip off the auto-generated prefix and suffix data.
					String combinedPackInfo = rawPackInfo;
					combinedPackInfo = combinedPackInfo.substring("models/item/".length(), combinedPackInfo.length() - ".json".length());
					
					//Get the pack information.
					String packID = combinedPackInfo.substring(0, combinedPackInfo.indexOf('.'));
					String systemName = combinedPackInfo.substring(combinedPackInfo.lastIndexOf('.') + 1);
					AItemPack<?> packItem = PackParserSystem.getItem(packID, systemName);
					
					//Get the actual resource path for this resource.
					String resourcePath = PackResourceLoader.getPackResource(packItem.definition, ResourceType.ITEM_JSON, systemName);
					
					//Try to load the item JSON, or create it if it doesn't exist.
					InputStream stream = getClass().getResourceAsStream(resourcePath);
					if(stream != null){
						return stream;
					}else{
						//Get the actual texture path.
						String itemTexturePath = PackResourceLoader.getPackResource(packItem.definition, ResourceType.ITEM_PNG, systemName);
						
						//Remove the "/assets/textures/" portion as it's implied with JSON.
						itemTexturePath = itemTexturePath.substring(("/assets/"  + packID + "/textures/").length());
						
						//Remove the .png suffix as it's also implied.
						itemTexturePath = itemTexturePath.substring(0, itemTexturePath.length() - ".png".length());
						
						//Need to add packID domain to this to comply with JSON domains.
						itemTexturePath = packID + ":" + itemTexturePath;
						
						//Generate fake JSON and return as stream to MC loader.
						String fakeJSON = "{\"parent\":\"mts:item/basic\",\"textures\":{\"layer0\": \"" + itemTexturePath + "\"}}";
						return new ByteArrayInputStream(fakeJSON.getBytes(StandardCharsets.UTF_8));
					}
				}else{
					//Strip off the auto-generated prefix and suffix data.
					String combinedPackInfo = rawPackInfo;
					combinedPackInfo = combinedPackInfo.substring("textures/".length(), combinedPackInfo.length() - ".png".length());
					
					//Get the pack information.
					//If we are ending in _item, it means we are getting a JSON for a modular-pack's item PNG.
					//Need to remove this suffix to get the correct systemName to look-up in the systems..
					String packID = packDomain;
					String systemName = combinedPackInfo.substring(combinedPackInfo.lastIndexOf('/') + 1);
					if(systemName.endsWith("_item")){
						systemName = systemName.substring(0, systemName.length() - "_item".length());
					}
					AItemPack<?> packItem = PackParserSystem.getItem(packID, systemName);
					
					//Get the actual resource path for this resource and return its stream.
					return getClass().getResourceAsStream(PackResourceLoader.getPackResource(packItem.definition, ResourceType.ITEM_PNG, systemName));
					//return getClass().getResourceAsStream("/assets/" + packDomain + "/" + rawPackInfo);
				}
			}catch(Exception e){
				MasterInterface.coreInterface.logError("ERROR: Could not parse out item JSON or PNG from: " + rawPackInfo);
				return null;
			}
		}

		@Override
		public boolean resourceExists(ResourceLocation location){
			return domains.contains(location.getResourceDomain()) 
					&& !location.getResourcePath().contains("blockstates") 
					&& !location.getResourcePath().contains("armatures") 
					&& !location.getResourcePath().contains("mcmeta")
					&& (location.getResourcePath().startsWith("models/item/") || location.getResourcePath().startsWith("textures/"));
		}

		@Override
		public Set<String> getResourceDomains(){
			return domains;
		}

		@Override
		public <T extends IMetadataSection> T getPackMetadata(MetadataSerializer metadataSerializer, String metadataSectionName) throws IOException{
			return null;
		}

		@Override
		public BufferedImage getPackImage() throws IOException{
			return null;
		}

		@Override
		public String getPackName(){
			return "Internal:" + packDomain;
		}
	}
	/*
	private static final ICustomModelLoader packModelLoader = new ICustomModelLoader(){

		@Override
		public void onResourceManagerReload(IResourceManager resourceManager){
			//Do nothing.  Packs don't change.
		}

		@Override
		public boolean accepts(ResourceLocation modelLocation){
			return modelLocation.getResourceDomain().equals("mts_packs");
		}

		@Override
		public IModel loadModel(ResourceLocation modelLocation) throws Exception{
			//Get the resource from the path.  Domain is mts_packs always.
			String resource = modelLocation.getResourcePath();
			
			//Strip off the mts_packs: prefix. 
			resource.substring("mts_packs:".length());
			
			//Get the pack information.
			String packID = resource.substring(0, resource.indexOf('.'));
			String systemName = resource.substring(resource.indexOf('.') + 1);
			AItemPack<?> packItem = PackParserSystem.getItem(packID, systemName);
			
			//Add the texture to the sprite system.
			TextureAtlasSprite itemSprite = new CustomTextureLoader(packItem.getRegistrationName());
			//TextureMap textureMap = Minecraft.getMinecraft().getTextureMapBlocks().registerSprite(modelLocation);
			
			
			//Return the Un-baked model.
			return packItem != null ? new UnbakedItemModelWrapper(packItem) : null;
		}
	};
	
	private static class CustomTextureLoader extends TextureAtlasSprite{
		public CustomTextureLoader(String spriteName){
			super(spriteName);
		}
	}
	
	private static class UnbakedItemModelWrapper implements IModel{
		private static final List<ResourceLocation> EMPTY_TEXTURE_LIST = new ArrayList<ResourceLocation>();
		
		private final AItemPack<?> packItem;
		
		UnbakedItemModelWrapper(AItemPack<?> packItem){
			this.packItem = packItem;
		}
		
		@Override
		public Collection<ResourceLocation> getTextures(){
	        return EMPTY_TEXTURE_LIST;
	    }
	    
		@Override
		public IBakedModel bake(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter){
			
			//Get the texture location.
			final String texturePath = PackResourceLoader.getPackResource(packItem.definition, ResourceType.ITEM_PNG, packItem.definition.systemName);
			
			Minecraft.getMinecraft().getItemRenderer().renderItemInFirstPerson(partialTicks);
		}
	};
	
	private static class BakedItemModelWrapper implements IBakedModel{
		private static final List<BakedQuad> EMPTY_QUAD_LIST = new ArrayList<BakedQuad>();
		
		
		@Override
		public List<BakedQuad> getQuads(IBlockState state, EnumFacing side, long rand){
			if(side == null){
				int currentTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
				MasterInterface.renderInterface.bindTexture(texturePath);
				GL11.glBegin(GL11.GL_TRIANGLES);
                GL11.glTexCoord2f(0, 0);
                GL11.glVertex3f(0, 0, 0);
                GL11.glTexCoord2f(0, 1);
                GL11.glVertex3f(0, 1, 0);
                GL11.glTexCoord2f(1, 1);
                GL11.glVertex3f(1, 1, 0);
                GL11.glTexCoord2f(1, 0);
                GL11.glVertex3f(1, 0, 0);
                GL11.glEnd();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture);
			}
			return EMPTY_QUAD_LIST;
		}

		@Override
		public boolean isAmbientOcclusion(){
			//Not a block, don't care.
			return false;
		}

		@Override
		public boolean isGui3d(){
			//3D models just look better.
			return true;
		}

		@Override
		public boolean isBuiltInRenderer(){
			//This smells like code that will go away sometime...
			return false;
		}

		@Override
		public TextureAtlasSprite getParticleTexture(){
			return null;
		}

		@Override
		public ItemOverrideList getOverrides(){
			return ItemOverrideList.NONE;
		}
	};*/
}
