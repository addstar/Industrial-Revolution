package me.steven.indrev.recipes.machines

import alexiil.mc.lib.attributes.fluid.amount.FluidAmount
import alexiil.mc.lib.attributes.fluid.volume.FluidKeys
import alexiil.mc.lib.attributes.fluid.volume.FluidVolume
import com.google.gson.JsonObject
import me.steven.indrev.utils.getFluidFromJson
import me.steven.indrev.utils.getItemStackFromJson
import me.steven.indrev.utils.identifier
import net.minecraft.inventory.Inventory
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.recipe.Ingredient
import net.minecraft.recipe.Recipe
import net.minecraft.recipe.RecipeSerializer
import net.minecraft.recipe.RecipeType
import net.minecraft.util.Identifier
import net.minecraft.util.collection.DefaultedList
import net.minecraft.util.registry.Registry
import net.minecraft.world.World

class FluidInfuserRecipe(
    private val id: Identifier,
    val processTime: Int,
    val inputFluid: FluidVolume,
    val ingredients: DefaultedList<Ingredient>,
    private val output: ItemStack
) : Recipe<Inventory> {
    override fun craft(inv: Inventory?): ItemStack = output.copy()

    override fun getId(): Identifier = id

    override fun getType(): RecipeType<*> = TYPE

    override fun fits(width: Int, height: Int): Boolean = true

    override fun getSerializer(): RecipeSerializer<*> = SERIALIZER

    override fun getOutput(): ItemStack = output

    override fun getPreviewInputs(): DefaultedList<Ingredient> = ingredients

    override fun matches(inv: Inventory?, world: World?): Boolean = ingredients.all { ingredient ->
        (0 until (inv?.size() ?: 0)).any { ingredient.test(inv?.getStack(it)) }
    }

    fun matches(inv: Inventory?, fluidVolume: FluidVolume, world: World?): Boolean =
        ingredients.all { ingredient ->
            (0 until (inv?.size() ?: 0)).any { ingredient.test(inv?.getStack(it)) }
        } && fluidVolume.fluidKey == inputFluid.fluidKey && fluidVolume.amount() >= inputFluid.amount()

    companion object {
        val TYPE = object : RecipeType<FluidInfuserRecipe> {}
        val SERIALIZER = Serializer()
        val IDENTIFIER = identifier("fluid_infuse")

        class Serializer : RecipeSerializer<FluidInfuserRecipe> {
            override fun write(buf: PacketByteBuf, recipe: FluidInfuserRecipe) {
                buf.writeInt(recipe.ingredients.size)
                recipe.ingredients.forEach { it.write(buf) }
                buf.writeInt(recipe.processTime)
                buf.writeItemStack(recipe.output)
                buf.writeIdentifier(recipe.inputFluid.fluidKey.entry.id)
                recipe.inputFluid.amount().toMcBuffer(buf)
            }

            override fun read(id: Identifier, json: JsonObject): FluidInfuserRecipe {
                val inputArray = json.getAsJsonArray("ingredients")
                val ingredients = inputArray.map { Ingredient.fromJson(it.asJsonObject) }.let { list ->
                    val ing = DefaultedList.ofSize(inputArray.size(), Ingredient.EMPTY)
                    list.forEachIndexed { index, ingredient -> ing[index] = ingredient }
                    ing
                }
                val fluid = getFluidFromJson(json.getAsJsonObject("fluid"))
                val output = getItemStackFromJson(json.getAsJsonObject("output"))
                val ticks = json.get("processTime").asInt
                return FluidInfuserRecipe(id, ticks, fluid, ingredients, output)
            }

            override fun read(id: Identifier, buf: PacketByteBuf): FluidInfuserRecipe {
                val size = buf.readInt()
                val input = DefaultedList.ofSize(size, Ingredient.EMPTY)
                (0 until size).forEach { i -> input[i] = Ingredient.fromPacket(buf) }
                val processTime = buf.readInt()
                val output = buf.readItemStack()
                val fluidId = buf.readIdentifier()
                val fluidAmount = FluidAmount.fromMcBuffer(buf)
                val fluidKey = FluidKeys.get(Registry.FLUID.get(fluidId))
                val fluidVolume = fluidKey.withAmount(fluidAmount)
                return FluidInfuserRecipe(id, processTime, fluidVolume, input, output)
            }

        }
    }
}