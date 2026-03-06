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
    private const val METADATA_FIELD_COUNT = 3

    /** Metadata serialized into view privateMetadata to survive modal updates. */
    data class ModalMetadata(
        val typeKey: String = "",
        val channelId: String? = null,
        val channelName: String? = null,
    )

    /** Encodes metadata as `typeKey|channelId|channelName` (pipe-delimited). */
    fun encodeMetadata(
        metadata: ModalMetadata
    ): String = "${metadata.typeKey}|${metadata.channelId ?: ""}|${metadata.channelName ?: ""}"

    fun decodeMetadata(raw: String?): ModalMetadata {
        if (raw.isNullOrBlank()) return ModalMetadata()
        val parts = raw.split("|", limit = METADATA_FIELD_COUNT)
        return if (parts.size == METADATA_FIELD_COUNT) {
            ModalMetadata(
                typeKey = parts[0],
                channelId = parts[1].ifBlank { null },
                channelName = parts[2].ifBlank { null },
            )
        } else {
            // Legacy: raw string was just the typeKey
            ModalMetadata(typeKey = raw)
        }
    }

    fun buildInitialTypeSelectionModal(
        types: List<NotificationTypeDto>,
        channelId: String? = null,
        channelName: String? = null,
    ): View =
        view { v ->
            v
                .callbackId("create_subscription_modal")
                .type("modal")
                .notifyOnClose(true)
                .privateMetadata(encodeMetadata(ModalMetadata(channelId = channelId, channelName = channelName)))
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
                        if (channelId != null) {
                            context {
                                elements {
                                    markdownText(
                                        "Opened in *#$channelName* — you can subscribe this channel after selecting an event type.",
                                    )
                                }
                            }
                        }
                    },
                )
        }

    fun buildDynamicSubscriptionModal(
        selectedTypeKey: String,
        availableTypes: List<NotificationTypeDto>,
        filters: List<FilterDefinitionDto>,
        channelId: String? = null,
        channelName: String? = null,
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

                    filters.forEach { filterDef ->
                        val fieldName = filterDef.field
                        input {
                            blockId("filter_block_$fieldName")
                            label("Filter by $fieldName (Optional)")
                            optional(true)
                            plainTextInput { actionId("filter_input_$fieldName") }
                        }
                    }

                    input {
                        blockId("channels_block")
                        label("Delivery Channels")
                        checkboxes {
                            actionId("channels_checkboxes")
                            options {
                                option {
                                    plainText("Slack DM (you)")
                                    value("slack_dm")
                                }
                                if (channelId != null) {
                                    option {
                                        plainText("#$channelName")
                                        value("channel:$channelId")
                                    }
                                }
                            }
                            initialOptions {
                                option {
                                    plainText("Slack DM (you)")
                                    value("slack_dm")
                                }
                            }
                        }
                    }

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
                            initialValue = "immediate",
                        )
                    }
                }
            v
                .callbackId("create_subscription_modal")
                .type("modal")
                .privateMetadata(
                    encodeMetadata(
                        ModalMetadata(typeKey = selectedTypeKey, channelId = channelId, channelName = channelName)
                    )
                )
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
