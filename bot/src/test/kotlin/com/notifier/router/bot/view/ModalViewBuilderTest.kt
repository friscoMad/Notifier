package com.notifier.router.bot.view

import com.notifier.router.bot.view.ModalViewBuilder.fieldLabel
import com.notifier.router.bot.view.ModalViewBuilder.fieldPlaceholder
import com.notifier.router.common.dto.FilterDefinitionDto
import com.notifier.router.common.dto.NotificationTypeDto
import com.slack.api.model.block.InputBlock
import com.slack.api.model.block.element.PlainTextInputElement
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ModalViewBuilderTest {

    // ── fieldLabel ────────────────────────────────────────────────────────────

    @Nested
    inner class FieldLabelTest {
        @Test fun `single word field is title-cased`() = assertEquals("Author", fieldLabel("author"))

        @Test fun `snake_case is converted to Title Case`() = assertEquals("Base Branch", fieldLabel("base_branch"))

        @Test fun `multi-word snake_case is fully title-cased`() = assertEquals("Check Name", fieldLabel("check_name"))

        @Test fun `already uppercase is preserved`() = assertEquals("Repo", fieldLabel("repo"))
    }

    // ── fieldPlaceholder ──────────────────────────────────────────────────────

    @Nested
    inner class FieldPlaceholderTest {
        @Test fun `author field returns example names`() = assertTrue(fieldPlaceholder("author").isNotBlank())

        @Test fun `repo field returns example repo path`() = assertTrue(fieldPlaceholder("repo").contains("/"))

        @Test fun `base_branch field returns branch example`() = assertTrue(
            fieldPlaceholder("base_branch").isNotBlank()
        )

        @Test fun `unknown field returns fallback text`() = assertEquals(
            "Enter value...",
            fieldPlaceholder("unknown_field")
        )

        @Test fun `all known fields return non-blank placeholder`() {
            listOf(
                "author", "repo", "base_branch", "reviewer", "check_name",
                "service", "environment", "status", "team", "test_name"
            )
                .forEach { field ->
                    assertTrue(fieldPlaceholder(field).isNotBlank(), "Expected non-blank placeholder for '$field'")
                }
        }
    }

    // ── buildDynamicSubscriptionModal ─────────────────────────────────────────

    @Nested
    inner class BuildDynamicSubscriptionModalTest {

        private val types = listOf(NotificationTypeDto(id = "1", key = "pr_created", name = "PR Created"))

        private val filters = listOf(
            FilterDefinitionDto(
                id = "f1",
                notificationTypeId = "1",
                field = "repo",
                fieldType = "string",
                operators = listOf("=", "!=", "contains"),
            ),
            FilterDefinitionDto(
                id = "f2",
                notificationTypeId = "1",
                field = "base_branch",
                fieldType = "string",
                operators = listOf("=", "!="),
            ),
        )

        @Test fun `modal has one input block per filter`() {
            val view = ModalViewBuilder.buildDynamicSubscriptionModal(
                selectedTypeKey = "pr_created",
                availableTypes = types,
                filters = filters,
            )
            val inputBlocks = view.blocks.filterIsInstance<InputBlock>()
            val filterInputs = inputBlocks.filter { it.blockId.startsWith("filter_block_") }
            assertEquals(2, filterInputs.size)
        }

        @Test fun `filter block ids use filter_block_ prefix`() {
            val view = ModalViewBuilder.buildDynamicSubscriptionModal(
                selectedTypeKey = "pr_created",
                availableTypes = types,
                filters = filters,
            )
            val inputBlocks = view.blocks.filterIsInstance<InputBlock>()
            assertTrue(inputBlocks.any { it.blockId == "filter_block_repo" })
            assertTrue(inputBlocks.any { it.blockId == "filter_block_base_branch" })
        }

        @Test fun `filter input action ids use filter_input_ prefix`() {
            val view = ModalViewBuilder.buildDynamicSubscriptionModal(
                selectedTypeKey = "pr_created",
                availableTypes = types,
                filters = filters,
            )
            val inputBlocks = view.blocks.filterIsInstance<InputBlock>()
            val repoBlock = inputBlocks.first { it.blockId == "filter_block_repo" }
            val inputElement = repoBlock.element as PlainTextInputElement
            assertEquals("filter_input_repo", inputElement.actionId)
        }

        @Test fun `filter label shows Title Case field name`() {
            val view = ModalViewBuilder.buildDynamicSubscriptionModal(
                selectedTypeKey = "pr_created",
                availableTypes = types,
                filters = filters,
            )
            val inputBlocks = view.blocks.filterIsInstance<InputBlock>()
            val branchBlock = inputBlocks.first { it.blockId == "filter_block_base_branch" }
            assertEquals("Base Branch", branchBlock.label.text)
        }

        @Test fun `filter hint contains all operators for that field`() {
            val view = ModalViewBuilder.buildDynamicSubscriptionModal(
                selectedTypeKey = "pr_created",
                availableTypes = types,
                filters = filters,
            )
            val inputBlocks = view.blocks.filterIsInstance<InputBlock>()
            val repoBlock = inputBlocks.first { it.blockId == "filter_block_repo" }
            val hint = repoBlock.hint?.text ?: ""
            assertTrue(hint.contains("="), "hint should contain '=': $hint")
            assertTrue(hint.contains("!="), "hint should contain '!=': $hint")
            assertTrue(hint.contains("contains"), "hint should contain 'contains': $hint")
        }

        @Test fun `filter hint mentions comma-separated multiple values`() {
            val view = ModalViewBuilder.buildDynamicSubscriptionModal(
                selectedTypeKey = "pr_created",
                availableTypes = types,
                filters = filters,
            )
            val inputBlocks = view.blocks.filterIsInstance<InputBlock>()
            val repoBlock = inputBlocks.first { it.blockId == "filter_block_repo" }
            val hint = repoBlock.hint?.text ?: ""
            assertTrue(hint.contains("comma", ignoreCase = true), "hint should mention commas: $hint")
        }

        @Test fun `filter inputs are optional`() {
            val view = ModalViewBuilder.buildDynamicSubscriptionModal(
                selectedTypeKey = "pr_created",
                availableTypes = types,
                filters = filters,
            )
            val inputBlocks = view.blocks.filterIsInstance<InputBlock>()
            inputBlocks.filter { it.blockId.startsWith("filter_block_") }.forEach { block ->
                assertTrue(block.isOptional, "Filter block '${block.blockId}' should be optional")
            }
        }

        @Test fun `filter input has contextual placeholder`() {
            val view = ModalViewBuilder.buildDynamicSubscriptionModal(
                selectedTypeKey = "pr_created",
                availableTypes = types,
                filters = filters,
            )
            val inputBlocks = view.blocks.filterIsInstance<InputBlock>()
            val repoBlock = inputBlocks.first { it.blockId == "filter_block_repo" }
            val inputElement = repoBlock.element as PlainTextInputElement
            assertNotNull(inputElement.placeholder)
            assertTrue(inputElement.placeholder.text.isNotBlank())
        }

        @Test fun `modal with no filters has no filter_block inputs`() {
            val view = ModalViewBuilder.buildDynamicSubscriptionModal(
                selectedTypeKey = "pr_created",
                availableTypes = types,
                filters = emptyList(),
            )
            val filterInputs = view.blocks.filterIsInstance<InputBlock>()
                .filter { it.blockId.startsWith("filter_block_") }
            assertTrue(filterInputs.isEmpty())
        }
    }
}
