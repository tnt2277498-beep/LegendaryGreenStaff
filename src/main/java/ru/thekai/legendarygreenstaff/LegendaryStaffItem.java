package ru.thekai.legendarygreenstaff;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class LegendaryStaffItem extends Item {

private static final int MAX_LEVEL = 10;
private static final int BASE_MAX_MANA = 100;

private static final String TAG_MANA = "StaffMana";
private static final String TAG_XP = "StaffXP";
private static final String TAG_SPELL = "StaffSpell";
private static final String TAG_FORM = "StaffForm";
private static final String TAG_OWNER = "StaffOwner";

private static final String[] SPELLS = {
        "Зелёный снаряд",
        "Исцеление",
        "Корни-ловушка",
        "Взрыв природы"
};

private static final String[] FORMS = {
        "Посох",
        "Меч",
        "Топор"
};

private static final int[] MANA_COSTS = {10, 25, 20, 35};
private static final int[] COOLDOWNS = {20, 100, 80, 120};

public LegendaryStaffItem(Properties properties) {
    super(properties);
}

// Shift + ПКМ по воздуху переключает заклинание.
// Обычный ПКМ применяет выбранное заклинание.
@Override
public InteractionResultHolder<ItemStack> use(
        Level level,
        Player player,
        InteractionHand hand
) {
    ItemStack stack = player.getItemInHand(hand);

    if (level.isClientSide) {
        return InteractionResultHolder.sidedSuccess(stack, true);
    }

    if (!checkOwner(stack, player)) {
        return InteractionResultHolder.fail(stack);
    }

    if (player.isShiftKeyDown()) {
        int next = (getSpell(stack) + 1) % SPELLS.length;
        stack.getOrCreateTag().putInt(TAG_SPELL, next);

        player.displayClientMessage(
                Component.literal("Заклинание: " + SPELLS[next])
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        level.playSound(
                null,
                player.blockPosition(),
                SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS,
                0.7F,
                1.2F
        );

        return InteractionResultHolder.success(stack);
    }

    if (player.getCooldowns().isOnCooldown(this)) {
        return InteractionResultHolder.fail(stack);
    }

    castSpell(level, player, hand, stack);
    return InteractionResultHolder.success(stack);
}

// Shift + ПКМ по блоку переключает форму оружия.
@Override
public InteractionResult useOn(UseOnContext context) {
    Player player = context.getPlayer();
    if (player == null) {
        return InteractionResult.PASS;
    }

    ItemStack stack = context.getItemInHand();
    Level level = context.getLevel();

    if (!player.isShiftKeyDown()) {
        return InteractionResult.PASS;
    }

    if (level.isClientSide) {
        return InteractionResult.SUCCESS;
    }

    if (!checkOwner(stack, player)) {
        return InteractionResult.FAIL;
    }

    int next = (getForm(stack) + 1) % FORMS.length;
    stack.getOrCreateTag().putInt(TAG_FORM, next);

    player.displayClientMessage(
            Component.literal("Форма: " + FORMS[next])
                    .withStyle(ChatFormatting.DARK_GREEN),
            true
    );

    level.playSound(
            null,
            player.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS,
            0.8F,
            0.8F + next * 0.25F
    );

    return InteractionResult.CONSUME;
}

private boolean checkOwner(ItemStack stack, Player player) {
    CompoundTag tag = stack.getOrCreateTag();

    if (!tag.hasUUID(TAG_OWNER)) {
        tag.putUUID(TAG_OWNER, player.getUUID());

        player.displayClientMessage(
                Component.literal("Посох признал тебя своим владельцем!")
                        .withStyle(ChatFormatting.GREEN),
                true
        );

        return true;
    }

    UUID owner = tag.getUUID(TAG_OWNER);

    if (!owner.equals(player.getUUID())) {
        player.displayClientMessage(
                Component.literal("Этот посох связан с другим владельцем!")
                        .withStyle(ChatFormatting.RED),
                true
        );
        return false;
    }

    return true;
}

private void castSpell(
        Level level,
        Player player,
        InteractionHand hand,
        ItemStack stack
) {
    int spell = getSpell(stack);
    int mana = getMana(stack);
    int cost = MANA_COSTS[spell];
    int staffLevel = getStaffLevel(stack);

    if (mana < cost) {
        player.displayClientMessage(
                Component.literal(
                        "Недостаточно маны: " + mana + "/" + getMaxMana(stack)
                ).withStyle(ChatFormatting.RED),
                true
        );
        return;
    }

    boolean success;

    switch (spell) {
        case 0:
            success = castBolt(level, player, staffLevel);
            break;
        case 1:
            success = castHeal(level, player);
            break;
        case 2:
            success = castRoots(level, player, staffLevel);
            break;
        case 3:
            success = castNatureBurst(level, player, staffLevel);
            break;
        default:
            success = false;
    }

    if (!success) {
        player.displayClientMessage(
                Component.literal("Заклинание не сработало.")
                        .withStyle(ChatFormatting.GRAY),
                true
        );
        return;
    }

    setMana(stack, mana - cost);
    addExperience(stack, 15);
    player.getCooldowns().addCooldown(this, COOLDOWNS[spell]);
    stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));

    player.displayClientMessage(
            Component.literal(
                    SPELLS[spell] + " | Мана: "
                            + getMana(stack) + "/" + getMaxMana(stack)
                            + " | Уровень: " + getStaffLevel(stack)
            ).withStyle(ChatFormatting.GREEN),
            true
    );
}

private boolean castBolt(Level level, Player player, int staffLevel) {
    LivingEntity target = findTarget(level, player, 18.0D);

    if (target == null) {
        return false;
    }

    boolean damaged = target.hurt(
            level.damageSources().magic(),
            6.0F + staffLevel * 0.5F
    );

    if (!damaged) {
        return false;
    }

    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.POISON,
            40 + staffLevel * 10,
            0
    ));

    if (level instanceof ServerLevel server) {
        spawnTrail(
                server,
                player.getEyePosition(),
                target.getBoundingBox().getCenter()
        );

        server.sendParticles(
                ParticleTypes.COMPOSTER,
                target.getX(),
                target.getY() + 1.0D,
                target.getZ(),
                16,
                0.3D,
                0.4D,
                0.3D,
                0.03D
        );
    }

    level.playSound(
            null,
            target.blockPosition(),
            SoundEvents.BEACON_POWER_SELECT,
            SoundSource.PLAYERS,
            0.8F,
            1.5F
    );

    return true;
}

private boolean castHeal(Level level, Player player) {
    boolean needsHealing = player.getHealth() < player.getMaxHealth()
            || player.hasEffect(net.minecraft.world.effect.MobEffects.POISON)
            || player.hasEffect(net.minecraft.world.effect.MobEffects.WITHER);

    if (!needsHealing) {
        return false;
    }

    player.heal(6.0F);
    player.removeEffect(net.minecraft.world.effect.MobEffects.POISON);
    player.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);

    if (level instanceof ServerLevel server) {
        server.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                player.getX(),
                player.getY() + 1.0D,
                player.getZ(),
                24,
                0.5D,
                0.8D,
                0.5D,
                0.05D
        );
    }

    level.playSound(
            null,
            player.blockPosition(),
            SoundEvents.AMETHYST_BLOCK_CHIME,
            SoundSource.PLAYERS,
            0.8F,
            1.4F
    );

    return true;
}

private boolean castRoots(Level level, Player player, int staffLevel) {
    LivingEntity target = findTarget(level, player, 16.0D);

    if (target == null) {
        return false;
    }

    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
            100 + staffLevel * 20,
            3
    ));

    target.addEffect(new net.minecraft.world.effect.MobEffectInstance(
            net.minecraft.world.effect.MobEffects.WEAKNESS,
            60 + staffLevel * 10,
            0
    ));

    if (level instanceof ServerLevel server) {
        server.sendParticles(
                ParticleTypes.COMPOSTER,
                target.getX(),
                target.getY() + 0.2D,
                target.getZ(),
                24,
                0.5D,
                0.2D,
                0.5D,
                0.02D
        );
    }

    level.playSound(
            null,
            target.blockPosition(),
            SoundEvents.ROOTED_DIRT_PLACE,
            SoundSource.PLAYERS,
            1.0F,
            0.8F
    );

    return true;
}

private boolean castNatureBurst(
        Level level,
        Player player,
        int staffLevel
) {
    List<LivingEntity> targets = level.getEntitiesOfClass(
            LivingEntity.class,
            player.getBoundingBox().inflate(5.0D),
            entity -> entity != player
                    && entity.isAlive()
                    && !player.isAlliedTo(entity)
    );

    if (targets.isEmpty()) {
        return false;
    }

    boolean hitSomething = false;

    for (LivingEntity target : targets) {
        boolean damaged = target.hurt(
                level.damageSources().magic(),
                5.0F + staffLevel * 0.4F
        );

        if (damaged) {
            target.knockback(
                    0.8D,
                    player.getX() - target.getX(),
                    player.getZ() - target.getZ()
            );
            hitSomething = true;
        }
    }

    if (!hitSomething) {
        return false;
    }

    if (level instanceof ServerLevel server) {
        server.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                player.getX(),
                player.getY() + 1.0D,
                player.getZ(),
                50,
                2.5D,
                1.0D,
                2.5D,
                0.08D
        );
    }

    level.playSound(
            null,
            player.blockPosition(),
            SoundEvents.GENERIC_EXPLODE,
            SoundSource.PLAYERS,
            0.7F,
            1.4F
    );

    return true;
}

private LivingEntity findTarget(
        Level level,
        Player player,
        double range
) {
    Vec3 start = player.getEyePosition();
    Vec3 look = player.getLookAngle();
    Vec3 end = start.add(look.scale(range));

    BlockHitResult blockHit = level.clip(new ClipContext(
            start,
            end,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            player
    ));

    double maxDistance = blockHit.getType() == HitResult.Type.MISS
            ? range
            : start.distanceTo(blockHit.getLocation());

    AABB box = player.getBoundingBox()
            .expandTowards(look.scale(range))
            .inflate(2.0D);

    LivingEntity closest = null;
    double closestDistance = maxDistance;

    for (LivingEntity candidate : level.getEntitiesOfClass(
            LivingEntity.class,
            box,
            entity -> entity != player
                    && entity.isAlive()
                    && !player.isAlliedTo(entity)
    )) {
        Optional<Vec3> hit = candidate.getBoundingBox()
                .inflate(0.3D)
                .clip(start, end);

        if (hit.isEmpty()) {
            continue;
        }

        double distance = start.distanceTo(hit.get());

        if (distance < closestDistance) {
            closest = candidate;
            closestDistance = distance;
        }
    }

    return closest;
}

private void spawnTrail(
        ServerLevel level,
        Vec3 start,
        Vec3 end
) {
    Vec3 delta = end.subtract(start);
    int steps = Math.max(1, (int) (delta.length() * 3.0D));

    for (int i = 0; i <= steps; i++) {
        Vec3 point = start.add(delta.scale((double) i / steps));

        level.sendParticles(
                ParticleTypes.COMPOSTER,
                point.x,
                point.y,
                point.z,
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D
        );
    }
}

@Override
public void inventoryTick(
        ItemStack stack,
        Level level,
        Entity entity,
        int slot,
        boolean selected
) {
    if (!(entity instanceof Player) || level.isClientSide) {
        return;
    }

    long interval = selected ? 20L : 60L;

    if (level.getGameTime() % interval != 0L) {
        return;
    }

    int mana = getMana(stack);
    int maxMana = getMaxMana(stack);

    if (mana < maxMana) {
        setMana(stack, mana + 1);
    }
}

@Override
public void appendHoverText(
        ItemStack stack,
        Level level,
        List<Component> tooltip,
        TooltipFlag flag
) {
    super.appendHoverText(stack, level, tooltip, flag);

    tooltip.add(Component.literal("Форма: " + FORMS[getForm(stack)])
            .withStyle(ChatFormatting.DARK_GREEN));

    tooltip.add(Component.literal("Заклинание: " + SPELLS[getSpell(stack)])
            .withStyle(ChatFormatting.GREEN));

    tooltip.add(Component.literal(
            "Мана: " + getMana(stack) + "/" + getMaxMana(stack)
    ).withStyle(ChatFormatting.AQUA));

    tooltip.add(Component.literal(
            "Уровень: " + getStaffLevel(stack) + "/" + MAX_LEVEL
    ).withStyle(ChatFormatting.LIGHT_PURPLE));

    tooltip.add(Component.literal(
            "Опыт: " + getExperience(stack) + "/"
                    + getStaffLevel(stack) * 100
    ).withStyle(ChatFormatting.GRAY));

    tooltip.add(Component.literal("Shift + ПКМ по воздуху: заклинание")
            .withStyle(ChatFormatting.DARK_GRAY));

    tooltip.add(Component.literal("Shift + ПКМ по блоку: форма")
            .withStyle(ChatFormatting.DARK_GRAY));
}

private int getSpell(ItemStack stack) {
    return Math.floorMod(
            stack.getOrCreateTag().getInt(TAG_SPELL),
            SPELLS.length
    );
}

private int getForm(ItemStack stack) {
    return Math.floorMod(
            stack.getOrCreateTag().getInt(TAG_FORM),
            FORMS.length
    );
}

private int getMana(ItemStack stack) {
    CompoundTag tag = stack.getOrCreateTag();

    return tag.contains(TAG_MANA)
            ? Math.max(0, tag.getInt(TAG_MANA))
            : BASE_MAX_MANA;
}

private void setMana(ItemStack stack, int amount) {
    stack.getOrCreateTag().putInt(
            TAG_MANA,
            Math.max(0, Math.min(getMaxMana(stack), amount))
    );
}

private int getExperience(ItemStack stack) {
    return Math.max(0, stack.getOrCreateTag().getInt(TAG_XP));
}

private int getStaffLevel(ItemStack stack) {
    return Math.min(MAX_LEVEL, getExperience(stack) / 100 + 1);
}

private int getMaxMana(ItemStack stack) {
    return BASE_MAX_MANA + (getStaffLevel(stack) - 1) * 10;
}

private void addExperience(ItemStack stack, int amount) {
    CompoundTag tag = stack.getOrCreateTag();
    tag.putInt(TAG_XP, getExperience(stack) + amount);
}

          }
