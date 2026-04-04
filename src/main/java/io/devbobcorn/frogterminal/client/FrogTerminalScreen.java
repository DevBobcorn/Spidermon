package io.devbobcorn.frogterminal.client;

import java.util.List;

import io.devbobcorn.frogterminal.block.FrogTerminalBlockEntity;
import io.devbobcorn.frogterminal.block.FrogTerminalMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.phys.Vec3;

public class FrogTerminalScreen extends AbstractContainerScreen<FrogTerminalMenu> {

	private static final int BG_COLOR = 0xCC101010;
	private static final int BORDER_COLOR = 0xFF9ede73;
	private static final int TITLE_COLOR = 0x9ede73;
	private static final int LABEL_COLOR = 0xAAAAAA;
	private static final int VALUE_COLOR = 0xFFFFFF;
	private static final int ERROR_COLOR = 0xFF7171;
	private static final int LINE_HEIGHT = 10;

	private List<BlockPos> connectedConveyors = List.of();

	public FrogTerminalScreen(FrogTerminalMenu menu, Inventory playerInventory, Component title) {
		super(menu, playerInventory, title);
		this.imageWidth = 200;
		this.imageHeight = 80;
	}

	@Override
	protected void init() {
		FrogTerminalBlockEntity be = menu.blockEntity;
		if (be != null) {
			connectedConveyors = be.getConnectedChainConveyors();
		}
		this.imageHeight = connectedConveyors.isEmpty()
			? 80
			: 80 + connectedConveyors.size() * LINE_HEIGHT;
		super.init();
	}

	@Override
	protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
		guiGraphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, BG_COLOR);
		guiGraphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, BORDER_COLOR);
	}

	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		super.render(guiGraphics, mouseX, mouseY, partialTick);
	}

	@Override
	protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		guiGraphics.drawCenteredString(font, this.title, imageWidth / 2, 8, TITLE_COLOR);

		FrogTerminalBlockEntity be = menu.blockEntity;
		if (be == null)
			return;

		if (be.target != null) {
			Vec3 location = be.getExactTargetLocation();
			if (location != Vec3.ZERO) {
				guiGraphics.drawCenteredString(font,
					Component.translatable("frogterminal.screen.chain_point"),
					imageWidth / 2, 30, LABEL_COLOR);
				String locStr = String.format("%.1f, %.1f, %.1f", location.x, location.y, location.z);
				guiGraphics.drawCenteredString(font, locStr, imageWidth / 2, 45, VALUE_COLOR);
			} else {
				guiGraphics.drawCenteredString(font,
					Component.translatable("frogterminal.screen.target_unavailable"),
					imageWidth / 2, 35, ERROR_COLOR);
			}
		} else {
			guiGraphics.drawCenteredString(font,
				Component.translatable("frogterminal.screen.no_target"),
				imageWidth / 2, 35, ERROR_COLOR);
		}

		if (!connectedConveyors.isEmpty()) {
			guiGraphics.drawCenteredString(font,
				Component.translatable("frogterminal.screen.connected_conveyors", connectedConveyors.size()),
				imageWidth / 2, 60, LABEL_COLOR);
			int y = 72;
			for (BlockPos pos : connectedConveyors) {
				String posStr = String.format("%d, %d, %d", pos.getX(), pos.getY(), pos.getZ());
				guiGraphics.drawCenteredString(font, posStr, imageWidth / 2, y, VALUE_COLOR);
				y += LINE_HEIGHT;
			}
		}
	}
}
