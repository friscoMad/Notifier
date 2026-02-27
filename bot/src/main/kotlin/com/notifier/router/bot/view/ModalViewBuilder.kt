package com.notifier.router.bot.view

import com.slack.api.model.block.Blocks.asBlocks
import com.slack.api.model.block.Blocks.input
import com.slack.api.model.block.Blocks.section
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.block.composition.BlockCompositions.option
import com.slack.api.model.block.composition.BlockCompositions.plainText
import com.slack.api.model.block.element.BlockElements.checkboxes
import com.slack.api.model.block.element.BlockElements.plainTextInput
import com.slack.api.model.block.element.BlockElements.staticSelect
import com.slack.api.model.view.View
import com.slack.api.model.view.Views.view
import com.slack.api.model.view.Views.viewClose
import com.slack.api.model.view.Views.viewSubmit
import com.slack.api.model.view.Views.viewTitle

object ModalViewBuilder {

    fun buildInitialTypeSelectionModal(types: List<Map<String, Any>>): View {
        return view { v ->
            v.callbackId("create_subscription_modal")
                .type("modal")
                .notifyOnClose(true)
                .title(viewTitle { t -> t.type("plain_text").text("Configure Notifications") })
                .submit(viewSubmit { s -> s.type("plain_text").text("Save") })
                .close(viewClose { c -> c.type("plain_text").text("Cancel") })
                .blocks(asBlocks(buildTypeSelectionBlock(types, null)))
        }
    }

    fun buildDynamicSubscriptionModal(
        selectedTypeKey: String,
        availableTypes: List<Map<String, Any>>,
        filters: List<Map<String, Any>>,
    ): View {
        val newBlocks = mutableListOf<LayoutBlock>()

        // 1. Re-add the Type Selection Block
        newBlocks.add(buildTypeSelectionBlock(availableTypes, selectedTypeKey))

        // 2. Add Dynamic Filter Inputs
        filters.forEach { filterDef ->
            val fieldName = filterDef["field"] as? String ?: "unknown"
            newBlocks.add(
                input { i ->
                    i.blockId("filter_block_$fieldName")
                        .optional(true)
                        .label(plainText("Filter by $fieldName (Optional)"))
                        .element(plainTextInput { pti -> pti.actionId("filter_input_$fieldName") })
                }
            )
        }

        // 3. Add Delivery Channel Preferences
        newBlocks.add(
            input { i ->
                i.blockId("channels_block")
                    .label(plainText("Delivery Channels"))
                    .element(
                        checkboxes { cb ->
                            cb.actionId("channels_checkboxes")
                                .options(
                                    listOf(
                                        option(plainText("Slack DM"), "slack_dm"),
                                        option(plainText("Email"), "email")
                                    )
                                )
                                .initialOptions(
                                    listOf(option(plainText("Slack DM"), "slack_dm"))
                                )
                        }
                    )
            }
        )

        // 4. Add Digest Speed Select
        newBlocks.add(
            input { i ->
                i.blockId("digest_block")
                    .label(plainText("Delivery Speed (Digesting)"))
                    .element(
                        staticSelect { select ->
                            select.actionId("digest_select")
                                .options(
                                    listOf(
                                        option(plainText("Immediate"), "immediate"),
                                        option(plainText("Daily (24h)"), "24h"),
                                        option(plainText("Half-Day (12h)"), "12h")
                                    )
                                )
                                .initialOption(option(plainText("Immediate"), "immediate"))
                        }
                    )
            }
        )

        return view { v ->
            v.callbackId("create_subscription_modal")
                .type("modal")
                .privateMetadata(selectedTypeKey)
                .title(viewTitle { t -> t.type("plain_text").text("Configure Notifications") })
                .submit(viewSubmit { s -> s.type("plain_text").text("Save") })
                .close(viewClose { c -> c.type("plain_text").text("Cancel") })
                .blocks(newBlocks)
        }
    }

    private fun buildTypeSelectionBlock(
        types: List<Map<String, Any>>,
        selectedTypeKey: String?
    ): com.slack.api.model.block.SectionBlock {
        val options = types.map { type ->
            val name = type["name"] as? String ?: "Unknown"
            val key = type["typeKey"] as? String ?: "unknown"
            option(plainText(name), key)
        }

        val currentOption = options.find { it.value == selectedTypeKey }

        return section { s ->
            s.blockId("type_block")
                .text(plainText("Which event do you want to be notified about?"))
                .accessory(
                    staticSelect { select ->
                        select.actionId("type_select")
                            .placeholder(plainText("Select an event..."))
                            .options(options)
                            .let { if (currentOption != null) it.initialOption(currentOption) else it }
                    }
                )
        }
    }
}
