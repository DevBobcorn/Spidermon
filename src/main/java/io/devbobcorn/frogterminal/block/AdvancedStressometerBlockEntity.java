package io.devbobcorn.frogterminal.block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.simibubi.create.content.kinetics.KineticNetwork;
import com.simibubi.create.content.kinetics.base.IRotate.StressImpact;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.item.TooltipHelper;
import com.simibubi.create.foundation.utility.CreateLang;

import net.createmod.catnip.lang.LangBuilder;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class AdvancedStressometerBlockEntity extends KineticBlockEntity {

    public float dialTarget;
    public float dialState;
    public float prevDialState;
    public int color;

    private final List<StressEntry> sourceEntries = new ArrayList<>();
    private final List<StressEntry> consumerEntries = new ArrayList<>();

    private record StressEntry(String descriptionId, int count, float value, float rpm) {}

    public AdvancedStressometerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
    }

    @Override
    public void updateFromNetwork(float maxStress, float currentStress, int networkSize) {
        super.updateFromNetwork(maxStress, currentStress, networkSize);

        if (!StressImpact.isEnabled())
            dialTarget = 0;
        else if (isOverStressed())
            dialTarget = 1.125f;
        else if (maxStress == 0)
            dialTarget = 0;
        else
            dialTarget = currentStress / maxStress;

        if (dialTarget > 0) {
            if (dialTarget < .5f)
                color = Color.mixColors(0x00FF00, 0xFFFF00, dialTarget * 2);
            else if (dialTarget < 1)
                color = Color.mixColors(0xFFFF00, 0xFF0000, (dialTarget) * 2 - 1);
            else
                color = 0xFF0000;
        }

        if (hasNetwork())
            computeNetworkBreakdown();

        sendData();
        setChanged();
    }

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
        if (getSpeed() == 0) {
            dialTarget = 0;
            sourceEntries.clear();
            consumerEntries.clear();
            setChanged();
            return;
        }

        updateFromNetwork(capacity, stress, getOrCreateNetwork().getSize());
    }

    private void computeNetworkBreakdown() {
        KineticNetwork network = getOrCreateNetwork();

        Map<String, StressEntry> sourcesGrouped = new LinkedHashMap<>();
        for (KineticBlockEntity be : network.sources.keySet()) {
            String descId = be.getBlockState().getBlock().getDescriptionId();
            float rpm = Math.abs(be.getTheoreticalSpeed());
            String key = descId + "|" + rpm;
            float cap = network.getActualCapacityOf(be);
            StressEntry prev = sourcesGrouped.get(key);
            if (prev != null)
                sourcesGrouped.put(key, new StressEntry(descId, prev.count + 1, cap, rpm));
            else
                sourcesGrouped.put(key, new StressEntry(descId, 1, cap, rpm));
        }
        sourceEntries.clear();
        sourceEntries.addAll(sourcesGrouped.values());
        sourceEntries.sort((a, b) -> Float.compare(b.value * b.count, a.value * a.count));

        Map<String, StressEntry> consumersGrouped = new LinkedHashMap<>();
        for (KineticBlockEntity be : network.members.keySet()) {
            float actualStress = network.getActualStressOf(be);
            if (actualStress <= 0)
                continue;
            String descId = be.getBlockState().getBlock().getDescriptionId();
            float rpm = Math.abs(be.getTheoreticalSpeed());
            String key = descId + "|" + rpm;
            StressEntry prev = consumersGrouped.get(key);
            if (prev != null)
                consumersGrouped.put(key, new StressEntry(descId, prev.count + 1, actualStress, rpm));
            else
                consumersGrouped.put(key, new StressEntry(descId, 1, actualStress, rpm));
        }
        consumerEntries.clear();
        consumerEntries.addAll(consumersGrouped.values());
        consumerEntries.sort((a, b) -> Float.compare(b.value * b.count, a.value * a.count));
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (!StressImpact.isEnabled())
            return false;

        CreateLang.translate("gui.gauge.info_header")
            .forGoggles(tooltip);

        double capacity = getNetworkCapacity();
        double stressFraction = getNetworkStress() / (capacity == 0 ? 1 : capacity);

        CreateLang.translate("gui.stressometer.title")
            .style(ChatFormatting.GRAY)
            .forGoggles(tooltip);

        if (getTheoreticalSpeed() == 0) {
            CreateLang.text(TooltipHelper.makeProgressBar(3, 0))
                .translate("gui.stressometer.no_rotation")
                .style(ChatFormatting.DARK_GRAY)
                .forGoggles(tooltip);
        } else {
            StressImpact.getFormattedStressText(stressFraction)
                .forGoggles(tooltip);
            CreateLang.translate("gui.stressometer.capacity")
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

            double remainingCapacity = capacity - getNetworkStress();

            LangBuilder su = CreateLang.translate("generic.unit.stress");
            LangBuilder stressTip = CreateLang.number(remainingCapacity)
                .add(su)
                .style(StressImpact.of(stressFraction)
                    .getRelativeColor());

            if (remainingCapacity != capacity)
                stressTip.text(ChatFormatting.GRAY, " / ")
                    .add(CreateLang.number(capacity)
                        .add(su)
                        .style(ChatFormatting.DARK_GRAY));

            stressTip.forGoggles(tooltip, 1);

            addBreakdownTooltip(tooltip);
        }

        return true;
    }

    private void addBreakdownTooltip(List<Component> tooltip) {
        LangBuilder su = CreateLang.translate("generic.unit.stress");

        if (!sourceEntries.isEmpty()) {
            CreateLang.builder().add(Component.translatable("frogterminal.gui.stressometer.producing"))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

            for (StressEntry entry : sourceEntries) {
                LangBuilder line = CreateLang.builder()
                    .add(Component.translatable(entry.descriptionId));
                
                line.text(ChatFormatting.DARK_GRAY, " (")
                    .add(CreateLang.number(entry.rpm)
                        .style(ChatFormatting.YELLOW))
                    .text(ChatFormatting.DARK_GRAY, " RPM): ")
                    .add(CreateLang.number(entry.value)
                        .add(su)
                        .style(ChatFormatting.GREEN));
                
                if (entry.count > 1)
                    line.text(ChatFormatting.DARK_GRAY, " \u00d7" + entry.count);
                
                line.forGoggles(tooltip, 1);
            }
        }

        if (!consumerEntries.isEmpty()) {
            CreateLang.builder().add(Component.translatable("frogterminal.gui.stressometer.consuming"))
                .style(ChatFormatting.GRAY)
                .forGoggles(tooltip);

            for (StressEntry entry : consumerEntries) {
                LangBuilder line = CreateLang.builder()
                    .add(Component.translatable(entry.descriptionId));

                line.text(ChatFormatting.DARK_GRAY, " (")
                    .add(CreateLang.number(entry.rpm)
                        .style(ChatFormatting.YELLOW))
                    .text(ChatFormatting.DARK_GRAY, " RPM): ")
                    .add(CreateLang.number(entry.value)
                        .add(su)
                        .style(ChatFormatting.AQUA));

                if (entry.count > 1)
                    line.text(ChatFormatting.DARK_GRAY, " \u00d7" + entry.count);
                
                line.forGoggles(tooltip, 1);
            }
        }
    }

    public float getNetworkStress() {
        return stress;
    }

    public float getNetworkCapacity() {
        return capacity;
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putFloat("Value", dialTarget);
        compound.putInt("Color", color);
        writeBreakdown(compound);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        dialTarget = compound.getFloat("Value");
        color = compound.getInt("Color");
        readBreakdown(compound);
        super.read(compound, registries, clientPacket);
    }

    private void writeBreakdown(CompoundTag compound) {
        ListTag sourcesList = new ListTag();
        for (StressEntry entry : sourceEntries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", entry.descriptionId);
            tag.putInt("Count", entry.count);
            tag.putFloat("Value", entry.value);
            tag.putFloat("RPM", entry.rpm);
            sourcesList.add(tag);
        }
        compound.put("Sources", sourcesList);

        ListTag consumersList = new ListTag();
        for (StressEntry entry : consumerEntries) {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", entry.descriptionId);
            tag.putInt("Count", entry.count);
            tag.putFloat("Value", entry.value);
            tag.putFloat("RPM", entry.rpm);
            consumersList.add(tag);
        }
        compound.put("Consumers", consumersList);
    }

    private void readBreakdown(CompoundTag compound) {
        sourceEntries.clear();
        if (compound.contains("Sources", Tag.TAG_LIST)) {
            ListTag list = compound.getList("Sources", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                sourceEntries.add(new StressEntry(
                    tag.getString("Name"), tag.getInt("Count"), tag.getFloat("Value"), tag.getFloat("RPM")));
            }
        }

        consumerEntries.clear();
        if (compound.contains("Consumers", Tag.TAG_LIST)) {
            ListTag list = compound.getList("Consumers", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag tag = list.getCompound(i);
                consumerEntries.add(new StressEntry(
                    tag.getString("Name"), tag.getInt("Count"), tag.getFloat("Value"), tag.getFloat("RPM")));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        prevDialState = dialState;
        dialState += (dialTarget - dialState) * .125f;
        if (dialState > 1 && level.random.nextFloat() < 1 / 2f)
            dialState -= (dialState - 1) * level.random.nextFloat();
    }
}
