package com.notifier.router.bot.view

import com.notifier.router.common.dto.FilterDefinitionDto
import com.notifier.router.common.dto.NotificationTypeDto
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
                            markdownText("Which event do you want to be notified about?")
                            accessory {
                                staticSelect {
                                    actionId("type_select")
                                    placeholder("Select an event...")
                                    options {
                                        types.forEach { type ->
                                            option {
                                                markdownText(type.name)
                                                value(type.key)
                                            }
                                        }
                                    }
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
    ): View =
        view { v ->
            val selectedType = availableTypes.find { it.key == selectedTypeKey }
            val blocks =
                withBlocks {
                    section {
                        blockId("type_block")
                        markdownText("Which event do you want to be notified about?")
                        accessory {
                            staticSelect {
                                actionId("type_select")
                                placeholder("Select an event...")
                                options {
                                    availableTypes.forEach { type ->
                                        option {
                                            description(type.name)
                                            value(type.key)
                                        }
                                    }
                                }
                                if (selectedType != null) {
                                    initialOption {
                                        description(selectedType.name)
                                        value(selectedType.key)
                                    }
                                }
                            }
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
                                    description("Email")
                                    value("email")
                                }
                                option {
                                    description("Slack DM")
                                    value("slack_dm")
                                }
                            }
                            initialOptions {
                                option {
                                    description("Slack DM")
                                    value("slack_dm")
                                }
                            }
                        }
                    }
                    // 4. Add Digest Speed Select
                    input {
                        blockId("digest_block")
                        label("Delivery Speed (Digesting)")
                        staticSelect {
                            actionId("digest_select")
                            options {
                                option {
                                    description("Immediate")
                                    value("inmediate")
                                }
                                option {
                                    description("Daily (24h)")
                                    value("24h")
                                }
                                option {
                                    description("Half-Day (12h)")
                                    value("12h")
                                }
                            }
                            initialOption {
                                description("Immediate")
                                value("inmediate")
                            }
                        }
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
}
