package com.example.foodrescue.lot.web;

import com.example.foodrescue.common.BusinessException;
import com.example.foodrescue.common.DemoData;
import com.example.foodrescue.common.FoodCategory;
import com.example.foodrescue.common.ItemUnit;
import com.example.foodrescue.common.StorageCondition;
import com.example.foodrescue.lot.FoodItemRequest;
import com.example.foodrescue.lot.LotRequest;
import com.example.foodrescue.lot.LotService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.ZoneOffset;
import java.util.List;

@Controller
@RequestMapping("/lots")
public class LotWebController {

    private final LotService lotService;

    public LotWebController(LotService lotService) {
        this.lotService = lotService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("lots", lotService.findAll(null, null, null));
        return "lot/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasRole('DONOR')")
    public String showCreateForm(Model model) {
        model.addAttribute("form", new CreateLotForm("", null, "", null, null, null, null, "", null, null));
        addOptions(model);
        return "lot/create-form";
    }

    @PostMapping
    @PreAuthorize("hasRole('DONOR')")
    public String create(
            @Valid @ModelAttribute("form") CreateLotForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addOptions(model);
            return "lot/create-form";
        }
        try {
            lotService.create(toRequest(form));
        } catch (BusinessException ex) {
            bindingResult.reject("business", ex.getMessage());
            addOptions(model);
            return "lot/create-form";
        }
        redirectAttributes.addFlashAttribute("successMessage", "Лот успішно створено.");
        return "redirect:/lots";
    }

    private LotRequest toRequest(CreateLotForm form) {
        return new LotRequest(
                DemoData.DONOR,
                form.title(),
                form.category(),
                List.of(new FoodItemRequest(form.itemName(), form.itemQuantity(), form.itemUnit(), null)),
                form.totalWeightKg(),
                form.storageCondition(),
                form.pickupAddress(),
                form.pickupFrom().toInstant(ZoneOffset.UTC),
                form.pickupTo().toInstant(ZoneOffset.UTC));
    }

    private void addOptions(Model model) {
        model.addAttribute("categories", FoodCategory.values());
        model.addAttribute("units", ItemUnit.values());
        model.addAttribute("storages", StorageCondition.values());
    }
}
