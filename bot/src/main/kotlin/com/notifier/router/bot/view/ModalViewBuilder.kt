package com.notifier.router.bot.view

import com.slack.api.model.block.Blocks.asBlocks
import com.slack.api.model.block.Blocks.input
import com.slack.api.model.block.Blocks.section
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.block.composition.BlockCompositions.markdownText
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
    fun buildInitialTypeSelectionModal(types: List<Map<String, Any>>): View =
        view { v ->
            v
                .callbackId("create_subscription_modal")
                .type("modal")
                .notifyOnClose(true)
                .title(viewTitle { it.type("plain_text").text("Configure Notifications") })
                .submit(viewSubmit { it.type("plain_text").text("Save") })
                .close(viewClose { it.type("plain_text").text("Cancel") })
                .blocks(
                    asBlocks(
                        section { s ->
                            s
                                .blockId("type_block")
                                .text(
                                    markdownText(
                                        "Which event do you want to be notified about?",
                                    ),
                                ).accessory(
                                    staticSelect { sel ->
                                        sel
                                            .actionId("type_select")
                                            .placeholder(
                                                plainText(
                                                    "Select an event...",
                                                ),
                                            ).options(
                                                types.map { type ->
                                                    val name =
                                                        type["name"] as?
                                                            String
                                                            ?: "Unknown"
                                                    val key =
                                                        type[
                                                            "typeKey",
                                                        ] as?
                                                            String
                                                            ?: "unknown"
                                                    option(
                                                        plainText(name),
                                                        key,
                                                    )
                                                },
                                            )
                                    },
                                )
                        },
                    ),
                )
        }

    fun buildDynamicSubscriptionModal(
        selectedTypeKey: String,
        availableTypes: List<Map<String, Any>>,
        filters: List<Map<String, Any>>,
    ): View =
        view { v ->
            val selectedType = availableTypes.find { it["typeKey"] == selectedTypeKey }
            val blocksList =
                mutableListOf<LayoutBlock>(
                    section { s ->
                        s
                            .blockId("type_block")
                            .text(
                                markdownText(
                                    "Which event do you want to be notified about?",
                                ),
                            ).accessory(
                                staticSelect { sel ->
                                    sel
                                        .actionId("type_select")
                                        .placeholder(
                                            plainText("Select an event..."),
                                        ).options(
                                            availableTypes.map { type ->
                                                val name =
                                                    type["name"] as? String
                                                        ?: "Unknown"
                                                val key =
                                                    type["typeKey"] as?
                                                        String
                                                        ?: "unknown"
                                                option(plainText(name), key)
                                            },
                                        )
                                    if (selectedType != null) {
                                        sel.initialOption(
                                            option(
                                                plainText(
                                                    selectedType["name"] as
                                                        String,
                                                ),
                                                selectedType["typeKey"] as
                                                    String,
                                            ),
                                        )
                                    }
                                    sel
                                },
                            )
                    },
                )

            // 2. Add Dynamic Filter Inputs
            filters.forEach { filterDef ->
                val fieldName = filterDef["field"] as? String ?: "unknown"
                blocksList.add(
                    input { i ->
                        i
                            .blockId("filter_block_$fieldName")
                            .optional(true)
                            .label(plainText("Filter by $fieldName (Optional)"))
                            .element(plainTextInput { it.actionId("filter_input_$fieldName") })
                    },
                )
            }

            // 3. Add Delivery Channel Preferences
            blocksList.add(
                input { i ->
                    i
                        .blockId("channels_block")
                        .label(plainText("Delivery Channels"))
                        .element(
                            checkboxes { c ->
                                c
                                    .actionId("channels_checkboxes")
                                    .options(
                                        listOf(
                                            option(
                                                plainText("Slack DM"),
                                                "slack_dm",
                                            ),
                                            option(plainText("Email"), "email"),
                                        ),
                                    ).initialOptions(
                                        listOf(
                                            option(
                                                plainText("Slack DM"),
                                                "slack_dm",
                                            ),
                                        ),
                                    )
                            },
                        )
                },
            )

            // 4. Add Digest Speed Select
            blocksList.add(
                input { i ->
                    i
                        .blockId("digest_block")
                        .label(plainText("Delivery Speed (Digesting)"))
                        .element(
                            staticSelect { sel ->
                                sel
                                    .actionId("digest_select")
                                    .options(
                                        listOf(
                                            option(
                                                plainText("Immediate"),
                                                "immediate",
                                            ),
                                            option(
                                                plainText("Daily (24h)"),
                                                "24h",
                                            ),
                                            option(
                                                plainText("Half-Day (12h)"),
                                                "12h",
                                            ),
                                        ),
                                    ).initialOption(
                                        option(plainText("Immediate"), "immediate"),
                                    )
                                sel
                            },
                        )
                },
            )

            v
                .callbackId("create_subscription_modal")
                .type("modal")
                .privateMetadata(selectedTypeKey)
                .title(viewTitle { it.type("plain_text").text("Configure Notifications") })
                .submit(viewSubmit { it.type("plain_text").text("Save") })
                .close(viewClose { it.type("plain_text").text("Cancel") })
                .blocks(blocksList)
        }
}
