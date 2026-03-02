package com.notifier.router.bot.view

import com.notifier.router.common.dto.FilterDefinitionDto
import com.notifier.router.common.dto.NotificationTypeDto
import com.slack.api.model.kotlin_extension.block.element.dsl.BlockElementInputDsl
import com.slack.api.model.kotlin_extension.block.withBlocks
import com.slack.api.model.view.View
import com.slack.api.model.view.Views.view
import com.slack.api.model.view.Views.viewClose
import com.slack.api.model.view.Views.viewSubmit
import com.slack.api.model.view.Views.viewTitle

object ModalViewBuilder {
    fun buildInitialTypeSelectionModal(types: List<NotificationTypeDto>): View =
        view { v ->
            v
                .callbackId("create_subscription_modal")
                .type("modal")
                .notifyOnClose(true)
                .title(viewTitle { it.type("plain_text").text("Configure Notifications") })
                .submit(viewSubmit { it.type("plain_text").text("Save") })
                .close(viewClose { it.type("plain_text").text("Cancel") })
                .blocks(
                    withBlocks {
                        section {
                            blockId("type_block")
                            plainText("Which event do you want to be notified about?")
                            accessory {
                                staticSelect(
                                    actionId = "type_select",
                                    placeholder = "Select an event...",
                                    options = types.map { Option(it.name, it.key, it.description) },
                                )
                            }
                        }
                    },
                )
        }

    fun buildDynamicSubscriptionModal(
        selectedTypeKey: String,
        availableTypes: List<NotificationTypeDto>,
        filters: List<FilterDefinitionDto>,
    ): View =
        view { v ->
            val blocks =
                withBlocks {
                    section {
                        blockId("type_block")
                        plainText("Which event do you want to be notified about?")
                        accessory {
                            staticSelect(
                                actionId = "type_select",
                                placeholder = "Select an event...",
                                options = availableTypes.map { Option(it.name, it.key, it.description) },
                                initialValue = selectedTypeKey,
                            )
                        }
                    }

                    // 2. Add Dynamic Filter Inputs
                    filters.forEach { filterDef ->
                        val fieldName = filterDef.field
                        input {
                            blockId("filter_block_$fieldName")
                            label("Filter by $fieldName (Optional)")
                            optional(true)
                            plainTextInput { actionId("filter_input_$fieldName") }
                        }
                    }
                    // 3. Add Delivery Channel Preferences
                    input {
                        blockId("channels_block")
                        label("Delivery Channels")
                        checkboxes {
                            actionId("channels_checkboxes")
                            options {
                                option {
                                    plainText("Email")
                                    value("email")
                                }
                                option {
                                    plainText("Slack DM")
                                    value("slack_dm")
                                }
                            }
                            initialOptions {
                                option {
                                    plainText("Slack DM")
                                    value("slack_dm")
                                }
                            }
                        }
                    }
                    // 4. Add Digest Speed Select
                    input {
                        blockId("digest_block")
                        label("Delivery Speed (Digesting)")
                        staticSelect(
                            actionId = "digest_select",
                            options =
                                listOf(
                                    Option("Immediate", "immediate"),
                                    Option("Daily (24h)", "24h"),
                                    Option("Half-Day (12h)", "12h"),
                                ),
                            initialValue = "inmediate",
                        )
                    }
                }
            v
                .callbackId("create_subscription_modal")
                .type("modal")
                .privateMetadata(selectedTypeKey)
                .title(viewTitle { it.type("plain_text").text("Configure Notifications") })
                .submit(viewSubmit { it.type("plain_text").text("Save") })
                .close(viewClose { it.type("plain_text").text("Cancel") })
                .blocks(blocks)
        }

    private fun BlockElementInputDsl.staticSelect(
        actionId: String,
        placeholder: String? = null,
        options: List<Option>,
        initialValue: String? = null,
    ) {
        staticSelect {
            actionId(actionId)
            placeholder?.let { placeholder(it) }
            options {
                options.forEach { (text, value, desc) ->
                    option {
                        plainText(text)
                        value(value)
                        desc?.let { description(it) }
                    }
                }
            }
            if (initialValue != null) {
                options.find { it.value == initialValue }?.let { (text, value, desc) ->
                    initialOption {
                        plainText(text)
                        value(value)
                        desc?.let { description(it) }
                    }
                }
            }
        }
    }

    data class Option(
        val text: String,
        val value: String,
        val description: String? = null,
    )
}
